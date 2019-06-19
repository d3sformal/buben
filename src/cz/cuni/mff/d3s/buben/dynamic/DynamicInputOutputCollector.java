/*
 * Copyright (C) 2019, Charles University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cz.cuni.mff.d3s.buben.dynamic;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Stack;
import java.util.Iterator;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Value;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.ModificationWatchpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.VMDeathRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.ModificationWatchpointRequest;
import com.sun.jdi.request.ExceptionRequest;

import cz.cuni.mff.d3s.buben.Configuration;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.common.ProgramPoint;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;
import cz.cuni.mff.d3s.buben.jdi.JDIUtils;
import cz.cuni.mff.d3s.buben.analysis.SymbolicMethodLocationsCollector;
import cz.cuni.mff.d3s.buben.wala.WALAUtils;


public class DynamicInputOutputCollector
{
	// data structures for recorded method call results and side effects
	
	// map from method signature to a list of objects that contain return values 
	protected static Map<String, Set<CallResultInfo>> mthSig2CallResult;
	
	// map from method signatures to a set of modified object fields
	protected static Map<String, Set<FieldWriteInfo>> mthSig2FieldWrites;
	
	// map from method signatures to a set of modified array elements
	protected static Map<String, Set<ArrayWriteInfo>> mthSig2ArrayWrites;
	
	// map from method signatures to (reference, array content) pairs collected at method entry
	protected static Map<String, Map<Long, List<Expression>>> mthSig2EntryArraysFull;
	
	// map from method signatures to (reference, hash code) pairs collected at method entry
	protected static Map<String, Map<Long, Integer>> mthSig2EntryArraysHash;

	static
	{
		mthSig2CallResult = new LinkedHashMap<String, Set<CallResultInfo>>();
		mthSig2FieldWrites = new LinkedHashMap<String, Set<FieldWriteInfo>>();
		mthSig2ArrayWrites = new LinkedHashMap<String, Set<ArrayWriteInfo>>();

		mthSig2EntryArraysFull = new LinkedHashMap<String, Map<Long, List<Expression>>>();
		mthSig2EntryArraysHash = new LinkedHashMap<String, Map<Long, Integer>>();
	}
	
	
	public static Set<String> getInternallyTrackedMethods()
	{
		Set<String> methodSigs = new HashSet<String>();

		return methodSigs;
	}


	public static void collectInputsFieldUpdatesForMethods(Process suspendedProc, String mainClassName, Set<String> interceptionTargetMethods, Set<String> nativeMethods) throws Exception
	{
		long timeStart = System.currentTimeMillis();

		// connect debugger (JDI) to the JVM instance that is running the given program/test (note that the JVM is right now suspended)
		VirtualMachine jdiVM = JDIUtils.connectToJVM();

		// for every method in the given set, we must intercept the program execution (running test) at the method entry (first instruction)
		// native methods are handled in a bit special way: intercepting the program execution right before invocation (first instruction is not accessible to us)
		// our approach: now set all breakpoints that we can (for already loaded classes) and create a list of breakpoints that we will define later (when each respective class is actually loaded)
		
		// maps from a class name to a list of relevant breakpoint target locations that are in the class
		// "rem" stands for "remaining"
		Map<String, List<ProgramPoint>> remClsName2InvBreakLocs = new HashMap<String, List<ProgramPoint>>();
		
		for (String itMthSig : interceptionTargetMethods)
		{
			List<ProgramPoint> itMthInvokeLocs = SymbolicMethodLocationsCollector.getInvokeLocationsForMethod(itMthSig);

			// native method and abstracted methods do not have any bytecode
			if ( ( ! nativeMethods.contains(itMthSig) ) && ( ! WALAUtils.isMethodAbstractedInXML(itMthSig) ) ) 
			{
				// breakpoints are disabled now -> we will enable them at the entry to the procedure "main"
				JDIUtils.setMethodEntryBreakpoint(jdiVM, Utils.extractClassName(itMthSig), Utils.extractPlainMethodName(itMthSig), Utils.extractMethodParamRetDescriptor(itMthSig), false);
			}
			else
			{
				// breakpoints are disabled now -> we will enable them at the entry to the procedure "main"
				Map<String, List<ProgramPoint>> itMthRemCN2InvBL = JDIUtils.setMethodInvokeBreakpoints(jdiVM, itMthInvokeLocs, false);
			
				// save the remaining breakpoint locations at method invoke
				addRemainingBreakpointLocationsForInterceptedMethod(remClsName2InvBreakLocs, itMthRemCN2InvBL);
			}
		}

		// create breakpoints at field writes in already loaded classes
		// register modification watchpoints for every field of every class
		JDIUtils.createInitialFieldWriteBreakpoints(jdiVM);
		
		// we must also wait for notifications about JVM termination
		VMDeathRequest vmDeathReq = jdiVM.eventRequestManager().createVMDeathRequest();
		vmDeathReq.enable();
		
		// we have to monitor every new loaded class (to properly define breakpoints)
		ClassPrepareRequest clsPrepReq = jdiVM.eventRequestManager().createClassPrepareRequest();
		clsPrepReq.enable();

		boolean vmTerminated = false;
		
		RunState curState = new RunState();

		if (Configuration.DEBUG)
		{
			System.out.println("dynamic analysis with interception: start");
		}
		
		// resume the JVM and wait for events (method invocation, field write, etc)
		
		EventQueue jdiEvQueue = jdiVM.eventQueue();
			
		jdiVM.resume();
		
		while ( ! vmTerminated )
		{
			// wait for some new events
			EventSet events = jdiEvQueue.remove();
	
			for (Event ev : events)
			{
				if ((ev instanceof VMDeathEvent) || (ev instanceof VMDisconnectEvent))
				{
					vmTerminated = true;
					break;
				}
				
				if (ev instanceof ClassPrepareEvent)
				{
					ClassPrepareEvent cpEv = (ClassPrepareEvent) ev;

					ReferenceType clsRT = cpEv.referenceType();
					String clsName = clsRT.name();
					
					if (Configuration.DEBUG)
					{
						System.out.println("class initialized: name = " + clsName);
					}
					
					if (clsName.equals(mainClassName))
					{
						// create breakpoint for entry to the procedure "main"
						JDIUtils.setMethodEntryBreakpoint(jdiVM, mainClassName, "main", "([Ljava/lang/String;)V", true);
					}
					
					// set all breakpoints in the loaded class (method entry, before method invoke)
						
					for (String itMthSig : interceptionTargetMethods)
					{
						// such methods do not have any bytecode
						if (nativeMethods.contains(itMthSig) || WALAUtils.isMethodAbstractedInXML(itMthSig)) continue;
						
						if (itMthSig.startsWith(clsName+"."))
						{
							JDIUtils.setMethodEntryBreakpoint(jdiVM, clsName, Utils.extractPlainMethodName(itMthSig), Utils.extractMethodParamRetDescriptor(itMthSig), curState.isInsideMain());
						}
					}
		
					// we do not have to save the remaining breakpoint locations (as there should be none)
			
					List<ProgramPoint> clsInvBreakLocs = remClsName2InvBreakLocs.get(clsName);
					JDIUtils.setMethodInvokeBreakpoints(jdiVM, clsInvBreakLocs, curState.isInsideMain());
				
					// register modification watchpoints for every field of the newly loaded class				
					JDIUtils.addFieldWriteBreakpoints(jdiVM, clsRT, curState.isInsideMain());
  				}
				
				if (ev instanceof BreakpointEvent)
				{
					BreakpointEvent bpEv = (BreakpointEvent) ev;

					long threadID = bpEv.thread().uniqueID();
					ThreadReference threadRef = bpEv.thread();
					
					if (Configuration.DEBUG)
					{
						Location bpLoc = bpEv.location();
						
						System.err.println("breakpoint: location = " + JDIUtils.getMethodSignature(bpLoc.method()) + " [idx=" + bpLoc.codeIndex() + "]");
					}
					
					boolean entryToMain = false;
					
					if ( ! curState.isInsideMain() )
					{
						// check if the breakpoint location is at the entry to "main"						
						Method bpLocMth = bpEv.location().method();						
						if (bpLocMth.declaringType().name().equals(mainClassName) && bpLocMth.name().equals("main")) entryToMain = true;
					}
					
					if (entryToMain)
					{
						// we must enable all breakpoints at the entry to "main"
						List<BreakpointRequest> allBpReqs = jdiVM.eventRequestManager().breakpointRequests();
						for (BreakpointRequest br : allBpReqs) br.enable();
						
						curState.setInsideMain(true);
	
						// enable all field modification watchpoints for all threads
						JDIUtils.enableFieldWriteBreakpoints(jdiVM);

						continue;
					}

					// breakpoint at method entry point (pure Java method)
					if (bpEv.request().getProperty("mentry") != null)
					{
						// we are at the entry point of some intercepted method (pure Java)

						String methodSig = JDIUtils.getMethodSignature(bpEv.location().method());

						// we are just at the entry of a target method (that we want to intercept)
						if (interceptionTargetMethods.contains(methodSig))
						{
							if (Configuration.DEBUG)
							{
								System.err.println("method entry: signature = " + methodSig + ", thread = " + threadID);
							}

							// inspect the current stack frame to retrieve method call arguments of the array type
							// we can process only Java methods here because JDI does not allow to retrieve arguments for native methods
							if ( ! bpEv.location().method().isNative() )
							{
								StackFrame curThSF = bpEv.thread().frame(0);
								
								try
								{
									for (Value arg : curThSF.getArgumentValues())
									{
										// add reference into the list of tracked/relevant arrays
										if ((arg != null) && (arg instanceof ArrayReference))
										{
											ArrayReference argArray = (ArrayReference) arg;
											curState.getTrackedArrays(threadID).add(argArray);
										}
									}
								}
								catch (com.sun.jdi.InternalException ex)
								{
									System.err.println("[ERROR] JDI internal exception at method entry (retrieving argument values): mth sig = " + methodSig);
									ex.printStackTrace();
								}
							}

							Map<Long, List<Expression>> mthEntryArraysFull = mthSig2EntryArraysFull.get(methodSig);
							if (mthEntryArraysFull == null)
							{
								mthEntryArraysFull = new HashMap<Long, List<Expression>>();
								mthSig2EntryArraysFull.put(methodSig, mthEntryArraysFull);
							}
	
							Map<Long, Integer> mthEntryArraysHash = mthSig2EntryArraysHash.get(methodSig);
							if (mthEntryArraysHash == null)
							{
								mthEntryArraysHash = new HashMap<Long, Integer>();
								mthSig2EntryArraysHash.put(methodSig, mthEntryArraysHash);
							}

							Map<Long, List<Expression>> callEntryArraysFull = JDIUtils.saveArraysContentFull(jdiVM, curState.getTrackedArrays(threadID));
							Map<Long, Integer> callEntryArraysHash = JDIUtils.saveArraysContentHash(jdiVM, curState.getTrackedArrays(threadID));

							// save the information about arrays for the current method
							
							for (Map.Entry<Long, List<Expression>> me : callEntryArraysFull.entrySet())
							{
								mthEntryArraysFull.put(me.getKey(), me.getValue());
							}
							
							for (Map.Entry<Long, Integer> me : callEntryArraysHash.entrySet())
							{
								mthEntryArraysHash.put(me.getKey(), me.getValue());
							}

							// we clear the set of tracked arrays (references)
							// because the arrays should not be considered when some other method calls are processed later
							// the set is valid only while entry to some intercepted method (native, external) is processed
							curState.clearTrackedArrays(threadID);
						}
					}

					// breakpoint just before invocation of an intercepted method (native)
					if (bpEv.request().getProperty("invoke") != null)
					{
						String methodSig = (String) bpEv.request().getProperty("invoke");

						if (Configuration.DEBUG)
						{
							System.err.println("method invoke (native): caller signature = " + methodSig + ", thread = " + threadID);
						}

						// nothing to do here
					}
				}
				
				if (ev instanceof ModificationWatchpointEvent)
				{
					ModificationWatchpointEvent mwatchEv = (ModificationWatchpointEvent) ev;
					
					long threadID = mwatchEv.thread().uniqueID();

					// record the object identification (class name, unique ID), name of the modified field, and the new value
					
					Location mwatchLoc = mwatchEv.thread().frame(0).location();
						
					String methodSig = JDIUtils.getMethodSignature(mwatchLoc.method());	
					
					// we need to record field writes only in the library methods that access external entities
					if ( ! interceptionTargetMethods.contains(methodSig) ) continue;

					String className = null;
					ReferenceID objectRefID = null;
						
					if (mwatchEv.object() != null)
					{
						// instance field
						className = Utils.getPlainClassName(mwatchEv.object().type().signature());
						objectRefID = new ReferenceID(mwatchEv.object().uniqueID());
					}
					else
					{
						// static field
						className = Utils.getPlainClassName(mwatchEv.field().declaringType().signature());
						objectRefID = new ReferenceID(mwatchEv.field().declaringType().classObject().uniqueID());
					}
						
					String fieldName = mwatchEv.field().name();
						
					String fieldType = null;
						
					try
					{
						fieldType = Utils.getInternalTypeName(mwatchEv.field().type().signature());
					}
					catch (com.sun.jdi.ClassNotLoadedException ex) 
					{
						// ignore the field write event in this case
						continue;
					}
						
					boolean isStaticField = mwatchEv.field().isStatic();
						
					Expression newValue = null;
						
					try
					{
						newValue = JDIUtils.getValueAsSymbolicExpr(mwatchEv.valueToBe());
					}
					catch (com.sun.jdi.ClassNotLoadedException ex) 
					{
						// ignore the field write event in this case
						continue;
					}
						
					if (Configuration.DEBUG)
					{
						System.out.println("field modification: signature = " + methodSig + ", field name = " + className + "." + fieldName + ", field type = " + fieldType + ", new value = " + newValue);
					}
						
					FieldWriteInfo fwInfo = new FieldWriteInfo(className, objectRefID, fieldName, fieldType, isStaticField, newValue);
						
					Set<FieldWriteInfo> fwriteInfos = mthSig2FieldWrites.get(methodSig);
					if (fwriteInfos == null)
					{
						fwriteInfos = new HashSet<FieldWriteInfo>();
						mthSig2FieldWrites.put(methodSig, fwriteInfos);
					}
						
					fwriteInfos.add(fwInfo);
				}
			}
			
			// resume the suspended threads when all the received events are processed
			if ( ! vmTerminated ) events.resume();
		}
		
		// we do not have to clean (dispose) the JVM instance because it is already terminated (dead, disconnected) if we reached this point
		// not needed: jdiVM.dispose();
		
		if (Configuration.DEBUG)
		{
			System.out.println("dynamic analysis with interception: finish");
			System.out.println("");
		}
	}

	public static void collectOutputsArrayUpdatesForMethods(Process suspendedProc, String mainClassName, Set<String> interceptionTargetMethods, Set<String> nativeMethods) throws Exception
	{
		long timeStart = System.currentTimeMillis();

		// connect debugger (JDI) to the JVM instance that is running the given program/test (note that the JVM is right now suspended)
		VirtualMachine jdiVM = JDIUtils.connectToJVM();

		// for every method in the given set, we must intercept the program execution (running test) at method exit
		
		// we must also wait for notifications about JVM termination
		VMDeathRequest vmDeathReq = jdiVM.eventRequestManager().createVMDeathRequest();
		vmDeathReq.enable();
		
		// we have to monitor every new loaded class (to properly define breakpoints)
		ClassPrepareRequest clsPrepReq = jdiVM.eventRequestManager().createClassPrepareRequest();
		clsPrepReq.enable();

		boolean vmTerminated = false;
		
		RunState curState = new RunState();

		if (Configuration.DEBUG)
		{
			System.out.println("dynamic analysis with interception: start");
		}
		
		// resume the JVM and wait for events (method exit/return, exception)
		
		EventQueue jdiEvQueue = jdiVM.eventQueue();
			
		jdiVM.resume();
		
		while ( ! vmTerminated )
		{
			// wait for some new events
			EventSet events = jdiEvQueue.remove();
	
			for (Event ev : events)
			{
				if ((ev instanceof VMDeathEvent) || (ev instanceof VMDisconnectEvent))
				{
					vmTerminated = true;
					break;
				}
				
				if (ev instanceof ClassPrepareEvent)
				{
					ClassPrepareEvent cpEv = (ClassPrepareEvent) ev;

					ReferenceType clsRT = cpEv.referenceType();
					String clsName = clsRT.name();
					
					if (Configuration.DEBUG)
					{
						System.out.println("class initialized: name = " + clsName);
					}
					
					if (clsName.equals(mainClassName))
					{
						// create breakpoint for entry to the procedure "main"
						JDIUtils.setMethodEntryBreakpoint(jdiVM, mainClassName, "main", "([Ljava/lang/String;)V", true);
					}
					
					// register modification watchpoints for every field of the newly loaded class				
					JDIUtils.addFieldWriteBreakpoints(jdiVM, clsRT, curState.isInsideMain());
  				}
				
				if (ev instanceof BreakpointEvent)
				{
					BreakpointEvent bpEv = (BreakpointEvent) ev;

					long threadID = bpEv.thread().uniqueID();
					ThreadReference threadRef = bpEv.thread();
					
					if (Configuration.DEBUG)
					{
						Location bpLoc = bpEv.location();
						
						System.err.println("breakpoint: location = " + JDIUtils.getMethodSignature(bpLoc.method()) + " [idx=" + bpLoc.codeIndex() + "]");
					}
					
					boolean entryToMain = false;
					
					if ( ! curState.isInsideMain() )
					{
						// check if the breakpoint location is at the entry to "main"						
						Method bpLocMth = bpEv.location().method();						
						if (bpLocMth.declaringType().name().equals(mainClassName) && bpLocMth.name().equals("main")) entryToMain = true;
					}
					
					if (entryToMain)
					{
						// we must enable all breakpoints at the entry to "main"
						List<BreakpointRequest> allBpReqs = jdiVM.eventRequestManager().breakpointRequests();
						for (BreakpointRequest br : allBpReqs) br.enable();
						
						curState.setInsideMain(true);

						// enable monitoring of the method exit events
							// we consider just native methods called directly from application classes (that means we ignore deeply nested calls of native methods)
						MethodExitRequest mthExitReq = jdiVM.eventRequestManager().createMethodExitRequest();
						mthExitReq.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
						mthExitReq.enable();
	
						// we also have to monitor exceptions during the execution of intercepted methods (it is another way of method exit)
						ExceptionRequest excReq = jdiVM.eventRequestManager().createExceptionRequest(null, true, true);
						excReq.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
						excReq.enable();

						continue;
					}
				}
				
				if (ev instanceof MethodExitEvent)
				{
					MethodExitEvent mexitEv = (MethodExitEvent) ev;

					String methodSig = JDIUtils.getMethodSignature(mexitEv.method());

					long threadID = mexitEv.thread().uniqueID();
					ThreadReference threadRef = mexitEv.thread();

					// we are just at the exit from a target method (that we want to intercept)
					if (interceptionTargetMethods.contains(methodSig))
					{
						if (Configuration.DEBUG)
						{
							System.err.println("method exit: signature = " + methodSig + ", threadID = " + threadID);
						}

						// inspect the current stack frame to retrieve method call arguments of the array type
						// we can process only Java methods here because JDI does not allow to retrieve arguments for native methods
						if ( ! mexitEv.method().isNative() )
						{
							StackFrame curThSF = mexitEv.thread().frame(0);
							
							try
							{
								for (Value arg : curThSF.getArgumentValues())
								{
									// add reference into the list of tracked/relevant arrays
									if ((arg != null) && (arg instanceof ArrayReference))
									{
										ArrayReference argArray = (ArrayReference) arg;
										curState.getTrackedArrays(threadID).add(argArray);
									}
								}
							}
							catch (com.sun.jdi.InternalException ex)
							{
								System.err.println("[ERROR] JDI internal exception at method exit (retrieving argument values): mth sig = " + methodSig);
								ex.printStackTrace();
							}
						}

						// save result of the method (its return value)
						// we store just return values from method calls (of native methods and library methods accessing external entities) because argument values are not used/needed when generating abstraction
	
						CallResultInfo callRes = curState.makeFreshCallResultInfo(threadID);
					
						StackFrame curThSF = mexitEv.thread().frame(0);
							
						Value retVal = mexitEv.returnValue();
						callRes.returnValue = JDIUtils.getValueAsSymbolicExpr(retVal);
						
						// add reference into the list of tracked/relevant arrays
						if ((retVal != null) && (retVal instanceof ArrayReference))
						{
							ArrayReference retArray = (ArrayReference) retVal;
							curState.getTrackedArrays(threadID).add(retArray);
						}
						
						Set<CallResultInfo> callrInfos = mthSig2CallResult.get(methodSig);
						if (callrInfos == null)
						{
							callrInfos = new HashSet<CallResultInfo>();
							mthSig2CallResult.put(methodSig, callrInfos);
						}
					
						// we have to respect the configured upper bound
						// except for java.lang.Object.clone() that is filtered later
						if ((callrInfos.size() < Configuration.maxReturnValues) || methodSig.startsWith("java.lang.Object.clone"))
						{
							callrInfos.add(callRes);
						}
							
						// identify differences between arrays at method entry and exit
						
						Map<Long, List<Expression>> callExitArraysFull = JDIUtils.saveArraysContentFull(jdiVM, curState.getTrackedArrays(threadID));

						Map<Long, Integer> callExitArraysHash = JDIUtils.saveArraysContentHash(jdiVM, curState.getTrackedArrays(threadID));

						Map<Long, List<Expression>> mthEntryArraysFull = mthSig2EntryArraysFull.get(methodSig);
						Map<Long, Integer> mthEntryArraysHash = mthSig2EntryArraysHash.get(methodSig);

						// we ignore arrays that were not modified at all (when there is the same hash value for entry and exit)
						for (Iterator<ArrayReference> taIt = curState.getTrackedArrays(threadID).iterator(); taIt.hasNext(); )
						{
							ArrayReference taRef = taIt.next();
							
							Long taID = taRef.uniqueID();

							int entryHash = 0;
							if (mthEntryArraysHash != null)
							{
								Integer entryHashObj = mthEntryArraysHash.get(taID);
								if (entryHashObj != null) entryHash = entryHashObj.intValue();
							}

							int exitHash = 0;
							if (callExitArraysHash != null)
							{
								Integer exitHashObj = callExitArraysHash.get(taID);
								if (exitHashObj != null) exitHash = exitHashObj.intValue();
							}

							if (entryHash == exitHash) taIt.remove();
						}	

						Set<ArrayWriteInfo> callArrayWrites = JDIUtils.computeArrayModifications(jdiVM, curState.getTrackedArrays(threadID), mthEntryArraysFull, callExitArraysFull);

						if (Configuration.DEBUG)
						{
							System.err.println("array modifications: signature = " + methodSig);
							for (ArrayWriteInfo awInfo : callArrayWrites)
							{
								System.err.println("\t class name = " + awInfo.className + ", element index = " + awInfo.elementIndex + ", new value = " + awInfo.newValue);
							}
						}

						// we clear the set of tracked arrays (references)
						// because the arrays should not be considered when some other method calls are processed later
						// the set is valid only while entry to some intercepted method (native, external) is processed
						curState.clearTrackedArrays(threadID);

						// record all differences between arrays
						
						Set<ArrayWriteInfo> awriteInfos = mthSig2ArrayWrites.get(methodSig);
						if (awriteInfos == null)
						{
							awriteInfos = new HashSet<ArrayWriteInfo>();
							mthSig2ArrayWrites.put(methodSig, awriteInfos);
						}
						
						awriteInfos.addAll(callArrayWrites);
					}
					
					boolean exitFromMain = false;
					
					if (curState.isInsideMain())
					{
						// check if the method exit event is associated with "main"						
						if (methodSig.startsWith(mainClassName + ".main")) exitFromMain = true;
					}
					
					if (exitFromMain)
					{
						// we must disable all breakpoints at the exit from "main"
						List<BreakpointRequest> allBpReqs = jdiVM.eventRequestManager().breakpointRequests();
						for (BreakpointRequest br : allBpReqs) br.disable();
						
						curState.setInsideMain(false);
					}
				}

				if (ev instanceof ExceptionEvent)
				{
					ExceptionEvent excEv = (ExceptionEvent) ev;

					long threadID = excEv.thread().uniqueID();
					ThreadReference threadRef = excEv.thread();

					String throwLocMthSig = JDIUtils.getMethodSignature(excEv.location().method());
					
					// may be null if the exception is not caught
					String catchLocMthSig = null;
					if (excEv.catchLocation() != null) catchLocMthSig = JDIUtils.getMethodSignature(excEv.catchLocation().method());
	
					if (Configuration.DEBUG)
					{
						System.err.println("exception: throw location method signature = " + throwLocMthSig + ", catch location method signature = " + catchLocMthSig + ", threadID = " + threadID);
					}
				}
			}
			
			// resume the suspended threads when all the received events are processed
			if ( ! vmTerminated ) events.resume();
		}
		
		// we do not have to clean (dispose) the JVM instance because it is already terminated (dead, disconnected) if we reached this point
		// not needed: jdiVM.dispose();
		
		if (Configuration.DEBUG)
		{
			System.out.println("dynamic analysis with interception: finish");
			System.out.println("");
		}
	}
	
	public static Set<CallResultInfo> getCallResultsForMethod(String methodSig)
	{
		Set<CallResultInfo> callRes = mthSig2CallResult.get(methodSig);
		
		if (callRes == null) callRes = new HashSet<CallResultInfo>();
		
		return callRes;
	}
	
	public static Set<FieldWriteInfo> getFieldUpdatesForMethod(String methodSig)
	{
		Set<FieldWriteInfo> fwriteInfos = mthSig2FieldWrites.get(methodSig);
		
		if (fwriteInfos == null) fwriteInfos = new HashSet<FieldWriteInfo>();
		
		return fwriteInfos;
	}
	
	public static Set<ArrayWriteInfo> getArrayElementUpdatesForMethod(String methodSig)
	{
		Set<ArrayWriteInfo> awriteInfos = mthSig2ArrayWrites.get(methodSig);
		
		if (awriteInfos == null) awriteInfos = new HashSet<ArrayWriteInfo>();
		
		return awriteInfos;
	}
	
	public static void printMethodCallResults()
	{
		System.out.println("CALL RESULT");
		System.out.println("===========");

		// sort the set of method signatures
		Set<String> methodSigs = new TreeSet<String>();
		methodSigs.addAll(mthSig2CallResult.keySet());
		
		for (String mthSig : methodSigs)
		{
			if (Utils.isJavaStandardLibraryMethod(mthSig)) continue;

			Set<CallResultInfo> callrInfos = mthSig2CallResult.get(mthSig);

			System.out.println(mthSig);
			
			for (CallResultInfo crInfo : callrInfos)
			{
				System.out.println("\t " + crInfo);
			}
		}
		
		System.out.println("");
	}
	
	public static void printMethodSideEffectsFields()
	{
		System.out.println("UPDATED FIELDS");
		System.out.println("==============");

		// sort the set of method signatures
		Set<String> methodSigs = new TreeSet<String>();
		methodSigs.addAll(mthSig2FieldWrites.keySet());
		
		for (String mthSig : methodSigs)
		{
			if (Utils.isJavaStandardLibraryMethod(mthSig)) continue;
			
			Set<FieldWriteInfo> fwInfos = mthSig2FieldWrites.get(mthSig);

			System.out.println(mthSig);
			
			for (FieldWriteInfo fwi : fwInfos)
			{
				System.out.println("\t " + fwi);
			}
		}
		
		System.out.println("");
	}
	
	public static void printMethodSideEffectsArrays()
	{
		System.out.println("UPDATED ARRAYS");
		System.out.println("==============");

		// sort the set of method signatures
		Set<String> methodSigs = new TreeSet<String>();
		methodSigs.addAll(mthSig2ArrayWrites.keySet());
		
		for (String mthSig : methodSigs)
		{
			if (Utils.isJavaStandardLibraryMethod(mthSig)) continue;
			
			Set<ArrayWriteInfo> awInfos = mthSig2ArrayWrites.get(mthSig);

			System.out.println(mthSig);
			
			for (ArrayWriteInfo awi : awInfos)
			{
				System.out.println("\t " + awi);
			}
		}
		
		System.out.println("");
	}

	private static void addRemainingBreakpointLocationsForInterceptedMethod(Map<String, List<ProgramPoint>> remClsName2BreakLocs, Map<String, List<ProgramPoint>> itMthRemCN2BL)
	{
		for (Map.Entry<String, List<ProgramPoint>> me : itMthRemCN2BL.entrySet())
		{
			String clsName = me.getKey();
			List<ProgramPoint> breakLocs = me.getValue();
				
			List<ProgramPoint> remBreakLocs = remClsName2BreakLocs.get(clsName);
				
			if (remBreakLocs == null) 
			{
				remBreakLocs = breakLocs;
				remClsName2BreakLocs.put(clsName, remBreakLocs);
			}
			else
			{
				remBreakLocs.addAll(breakLocs);
			}
		}
	}


	static class RunState
	{
		// flag saying whether program execution already reached the entry to the procedure "main"
		// only this flag is global (i.e., not thread-specific)
		private boolean insideMain;
		
		// set of array objects that were passed as arguments into an intercepted method (native, external entities) or returned from such method
		// these array objects are the only subjects for detecting changes of their content (by intercepted methods)
		// we preserve the content of the set only within execution of an intercepted method (including nested calls)
		// we have to keep separate data for each thread because method calls interleave during program execution
		private Map<Long, Set<ArrayReference>> thread2TrackedArrays;
	

		public RunState()
		{
			insideMain = false;
			thread2TrackedArrays = new HashMap<Long, Set<ArrayReference>>();
		}

		public boolean isInsideMain()
		{
			return insideMain;
		}

		public void setInsideMain(boolean val)
		{
			insideMain = val;
		}

		public CallResultInfo makeFreshCallResultInfo(long threadID)
		{
			CallResultInfo cr = new CallResultInfo();

			return cr;
		}

		public Set<ArrayReference> getTrackedArrays(long threadID)
		{
			if ( ! thread2TrackedArrays.containsKey(threadID) )
			{
				thread2TrackedArrays.put(threadID, new HashSet<ArrayReference>());
			}

			return thread2TrackedArrays.get(threadID);
		}

		public void clearTrackedArrays(long threadID)
		{
			if ( ! thread2TrackedArrays.containsKey(threadID) )
			{
				thread2TrackedArrays.put(threadID, new HashSet<ArrayReference>());
			}
			else
			{
				thread2TrackedArrays.get(threadID).clear();
			}
		}
	}
}
