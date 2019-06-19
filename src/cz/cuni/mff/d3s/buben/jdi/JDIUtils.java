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
package cz.cuni.mff.d3s.buben.jdi;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ModificationWatchpointRequest;	
import com.sun.tools.jdi.SocketAttachingConnector;

import cz.cuni.mff.d3s.buben.Configuration;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.common.ProgramPoint;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ConstantExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.HeapReferenceExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.SpecialExpression;
import cz.cuni.mff.d3s.buben.dynamic.ReferenceID;
import cz.cuni.mff.d3s.buben.dynamic.ArrayWriteInfo;


public class JDIUtils
{
	// cached mappings between dynamic values and symbolic expressions
	private static Map<Value, Expression> value2SymbExpr;

	static
	{
		value2SymbExpr = new HashMap<Value, Expression>();
	}


	public static String getAgentConfig()
	{
		return ("-agentlib:jdwp=transport=dt_socket,address=127.0.0.1:"+Configuration.JDI_PORT+",server=y,suspend=y");
	}
	
	public static VirtualMachine connectToJVM() throws Exception
	{
		VirtualMachineManager vmMgr = Bootstrap.virtualMachineManager();
		
		SocketAttachingConnector socketConn = null;
		
		for (Connector conn : vmMgr.attachingConnectors())
		{
			if (conn instanceof SocketAttachingConnector) socketConn = (SocketAttachingConnector) conn;
		}
			
		Map<String, Argument> vmArgs = socketConn.defaultArguments();
		vmArgs.get("hostname").setValue("127.0.0.1");
		vmArgs.get("port").setValue(Configuration.JDI_PORT);
			
		VirtualMachine vm = socketConn.attach(vmArgs);
		
		return vm;
	}
	
	public static Map<String, List<ProgramPoint>> setMethodInvokeBreakpoints(VirtualMachine vm, List<ProgramPoint> mthInvokeLocs, boolean enableNow)
	{
		EventRequestManager evReqMgr = vm.eventRequestManager();
		
		// "rem" stands for "remaining" (to be defined later)
		Map<String, List<ProgramPoint>> remClsName2InvokeLocs = new HashMap<String, List<ProgramPoint>>();
		
		// empty list of method invoke locations
		if (mthInvokeLocs == null) return remClsName2InvokeLocs;
		
		for (ProgramPoint invPP : mthInvokeLocs)
		{
			// we consider just methods directly called from the application classes
			// we must do filtering here because of manually defined native methods (e.g., java.lang.System.arraycopy)
			if ( ! Configuration.isApplicationMethod(invPP.methodSig) ) continue;

			String ppClsName = Utils.extractClassName(invPP.methodSig);
			String ppMthName = Utils.extractPlainMethodName(invPP.methodSig);
			String ppMthShortSig = Utils.extractMethodParamRetDescriptor(invPP.methodSig);
			
			List<ReferenceType> refTypes = vm.classesByName(ppClsName);
			
			// the class not yet loaded
			if (refTypes.isEmpty())
			{
				List<ProgramPoint> clsInvokeLocs = remClsName2InvokeLocs.get(ppClsName);
				
				if (clsInvokeLocs == null)
				{
					clsInvokeLocs = new ArrayList<ProgramPoint>();
					remClsName2InvokeLocs.put(ppClsName, clsInvokeLocs);
				}
				
				clsInvokeLocs.add(invPP);
				
				continue;
			}
			
			// we assume there is only a single class with the given name
			ReferenceType clsRT = refTypes.get(0);
			
			Method ppMth = clsRT.methodsByName(ppMthName, ppMthShortSig).get(0);
			
			Location invokeLoc = ppMth.locationOfCodeIndex(invPP.insnPos);
			
			BreakpointRequest invokeBR = evReqMgr.createBreakpointRequest(invokeLoc);
			invokeBR.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
			invokeBR.putProperty("invoke", invPP.methodSig);
			
			if (enableNow) invokeBR.enable();
		}
		
		return remClsName2InvokeLocs;
	}
	
	public static Map<String, List<ProgramPoint>> setMethodReturnBreakpoints(VirtualMachine vm, List<ProgramPoint> mthReturnLocs, boolean enableNow)
	{
		EventRequestManager evReqMgr = vm.eventRequestManager();
		
		// "rem" stands for "remaining" (to be defined later)
		Map<String, List<ProgramPoint>> remClsName2ReturnLocs = new HashMap<String, List<ProgramPoint>>();
		
		// empty list of method return locations
		if (mthReturnLocs == null) return remClsName2ReturnLocs;
		
		for (ProgramPoint retPP : mthReturnLocs)
		{
			String ppClsName = Utils.extractClassName(retPP.methodSig);
			String ppMthName = Utils.extractPlainMethodName(retPP.methodSig);
			String ppMthDesc = Utils.extractMethodParamRetDescriptor(retPP.methodSig);
				
			List<ReferenceType> refTypes = vm.classesByName(ppClsName);
			
			// the class not yet loaded
			if (refTypes.isEmpty())
			{
				List<ProgramPoint> clsReturnLocs = remClsName2ReturnLocs.get(ppClsName);
				
				if (clsReturnLocs == null)
				{
					clsReturnLocs = new ArrayList<ProgramPoint>();
					remClsName2ReturnLocs.put(ppClsName, clsReturnLocs);
				}
				
				clsReturnLocs.add(retPP);
				
				continue;
			}
			
			// we assume there is only a single class with the given name
			ReferenceType clsRT = refTypes.get(0);
			
			Method ppMth = clsRT.methodsByName(ppMthName, ppMthDesc).get(0);
			
			Location returnLoc = ppMth.locationOfCodeIndex(retPP.insnPos);
			
			BreakpointRequest returnBR = evReqMgr.createBreakpointRequest(returnLoc);
			returnBR.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
			returnBR.putProperty("return", retPP.methodSig);
			
			if (enableNow) returnBR.enable();
		}
		
		return remClsName2ReturnLocs;
	}
	
	public static void setMethodEntryBreakpoint(VirtualMachine vm, String tgtClassName, String tgtMethodName, String tgtMethodDesc, boolean enableNow)
	{
		EventRequestManager evReqMgr = vm.eventRequestManager();
		
		List<ReferenceType> refTypes = vm.classesByName(tgtClassName);
	
		// the class not yet loaded
		if (refTypes.isEmpty()) return;

		// we assume there is only a single class with the given name
		ReferenceType tgtClsRT = refTypes.get(0);

		if (Configuration.DEBUG)
		{
			if (tgtClsRT.methodsByName(tgtMethodName, tgtMethodDesc).isEmpty())
			{
				System.err.println("[DEBUG] unknown method = " + tgtClassName + "." + tgtMethodName + tgtMethodDesc);
			}
		}

		// we assume there is only a single method with the given name
		Method tgtMth = tgtClsRT.methodsByName(tgtMethodName, tgtMethodDesc).get(0);
		
		if (Configuration.DEBUG)
		{
			if (tgtMth.isNative()) System.err.println("[DEBUG] setting entry breakpoint for native method: " + tgtClassName + "." + tgtMethodName + tgtMethodDesc);
		}

		Location entryLoc = tgtMth.locationOfCodeIndex(0);
			
		BreakpointRequest entryBR = evReqMgr.createBreakpointRequest(entryLoc);
		entryBR.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
		entryBR.putProperty("mentry", tgtClassName + "." + tgtMethodName + tgtMethodDesc);
		
		if (enableNow) entryBR.enable();
	}
		
	public static void createInitialFieldWriteBreakpoints(VirtualMachine vm)
	{
		EventRequestManager evReqMgr = vm.eventRequestManager();
		
		// for every loaded class, take all fields declared in it and create a modification watchpoint
		for (ReferenceType clsRT : vm.allClasses())
		{
			String clsName = Utils.getPlainClassName(clsRT.signature());
			
			// we ignore fields of classes that contain only methods accessing external entities
			if (Configuration.isExternalAccessClass(clsName)) continue;
			
			for (Field fld : clsRT.fields())
			{
				ModificationWatchpointRequest fldMwReq = evReqMgr.createModificationWatchpointRequest(fld);
				fldMwReq.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
			}
		}
	}

	public static void addFieldWriteBreakpoints(VirtualMachine vm, ReferenceType clsRT, boolean enableNow)
	{
		EventRequestManager evReqMgr = vm.eventRequestManager();
		
		// take all fields declared in the given class and create a modification watchpoint
		
		String clsName = Utils.getPlainClassName(clsRT.signature());
			
		// we ignore fields of classes that contain only methods accessing external entities
		if (Configuration.isExternalAccessClass(clsName)) return;
			
		for (Field fld : clsRT.fields())
		{
			ModificationWatchpointRequest fldMwReq = evReqMgr.createModificationWatchpointRequest(fld);
			fldMwReq.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);

			if (enableNow) fldMwReq.enable();
		}
	}

	public static void enableFieldWriteBreakpoints(VirtualMachine vm)
	{
		EventRequestManager evReqMgr = vm.eventRequestManager();

		// enable all field modification watchpoints for all threads

		for (ModificationWatchpointRequest mwReq : evReqMgr.modificationWatchpointRequests())
		{
			mwReq.enable();
		}
	}

	public static void disableFieldWriteBreakpointsForThread(VirtualMachine vm, ThreadReference thRef)
	{
		EventRequestManager evReqMgr = vm.eventRequestManager();

		// delete all field watchpoints associated with the given thread
		
		List<ModificationWatchpointRequest> fldMwRequests = evReqMgr.modificationWatchpointRequests();

		// list of requests to be deleted
		List<ModificationWatchpointRequest> thMwReqs = new ArrayList<ModificationWatchpointRequest>();

		for (ModificationWatchpointRequest mwReq : fldMwRequests)
		{
			Object propThID = mwReq.getProperty("threadID");
		
			// we must perform this check because thread-independent requests do not have this property
			if (propThID != null)
			{
				if (propThID.equals(thRef.uniqueID())) thMwReqs.add(mwReq);
			}
		}

		evReqMgr.deleteEventRequests(thMwReqs);
	}

	public static Map<Long, List<Expression>> saveArraysContentFull(VirtualMachine vm, Set<ArrayReference> relevantArrays) throws Exception
	{
		Map<Long, List<Expression>> array2ContentFull = new HashMap<Long, List<Expression>>();
		
		// in order to improve performance, we consider only array instances that are relevant (tracked)
		for (ArrayReference arrayObj : relevantArrays)
		{
			long arrayID = arrayObj.uniqueID();
			
			List<Value> arrayElementsRuntime = arrayObj.getValues();
			
			List<Expression> arrayElementsSymbolic = new ArrayList<Expression>();

			for (Value aeVal : arrayElementsRuntime)
			{
				Expression expr = getValueAsSymbolicExpr(aeVal);

				arrayElementsSymbolic.add(expr);
			}

			array2ContentFull.put(arrayID, arrayElementsSymbolic);
		}
		
		return array2ContentFull;
	}
	
	public static Map<Long, Integer> saveArraysContentHash(VirtualMachine vm, Set<ArrayReference> relevantArrays)
	{
		Map<Long, Integer> array2ContentHash = new HashMap<Long, Integer>();
		
		// in order to improve performance, we consider only array instances that are relevant (tracked)
		for (ArrayReference arrayObj : relevantArrays)
		{
			long arrayID = arrayObj.uniqueID();
			
			List<Value> arrayElementsRuntime = arrayObj.getValues();
			
			array2ContentHash.put(arrayID, arrayElementsRuntime.hashCode());
		}
		
		return array2ContentHash;
	}

	public static Set<ArrayWriteInfo> computeArrayModifications(VirtualMachine vm, Set<ArrayReference> relevantArrays, Map<Long, List<Expression>> oldArrays, Map<Long, List<Expression>> newArrays) throws Exception
	{
		Set<ArrayWriteInfo> awriteInfos = new HashSet<ArrayWriteInfo>();
	
		// in order to improve performance, we consider only array instances that are relevant (tracked)
		for (ArrayReference arrayObj : relevantArrays)
		{
			long arrayID = arrayObj.uniqueID();
					
			String arrayClsName = Utils.getPlainTypeName(arrayObj.type().signature());
			ReferenceID arrayObjRefID = new ReferenceID(arrayID);
					
			String elementTypeStr = Utils.getElementTypeFromArrayClassName(arrayClsName);
			
			List<Expression> newArrayElements = newArrays.get(arrayID);
					
			List<Expression> oldArrayElements = null;
			if (oldArrays != null) oldArrayElements = oldArrays.get(arrayID);
					
			// compare every element of the new array with corresponding element of the old array
			for (int i = 0; i < newArrayElements.size(); i++)
			{
				Expression newElement = newArrayElements.get(i);

				boolean modified = false;

				if (oldArrayElements == null)
				{
					// array did not exist before
					modified = true;
				}
				else
				{
					if (i >= oldArrayElements.size())
					{
						// array element did not exist before
						modified = true;
					}
					else
					{
						// element already existed before
								
						Expression oldElement = oldArrayElements.get(i);
								
						if (newElement == null)
						{
							if (oldElement != null) modified = true;
						}
						else
						{
							if ( ! newElement.equals(oldElement) ) modified = true;
						}
					}
				}
						
				if (modified)
				{
					int elementIndex = i;
					
					ArrayWriteInfo awInfo = new ArrayWriteInfo(arrayClsName, arrayObjRefID, elementIndex, elementTypeStr, newElement);
					
					awriteInfos.add(awInfo);
				}
			}
		}
		
		return awriteInfos;
	}
	
	public static String getMethodSignature(Method mth)
	{
		return mth.declaringType().name() + "." + mth.name() + mth.signature(); 
	}

	public static Expression getValueAsSymbolicExpr(Value val) throws Exception
	{
		// fast track (using cache)
		Expression cachedExpr = value2SymbExpr.get(val);
		if (cachedExpr != null) return cachedExpr;

		Expression expr = null;

		if (val == null)
		{
			expr = SpecialExpression.NULL;
		}
		else if ((val instanceof BooleanValue) || (val instanceof ByteValue) || (val instanceof CharValue) || (val instanceof IntegerValue) || (val instanceof ShortValue))
		{
			expr = new ConstantExpression(new Integer( ((PrimitiveValue) val).intValue() ));
		}
		else if (val instanceof LongValue)
		{
			expr = new ConstantExpression(new Long( ((LongValue) val).value() ));
		}
		else if (val instanceof FloatValue)
		{
			expr = new ConstantExpression(new Float( ((FloatValue) val).value() ));
		}
		else if (val instanceof DoubleValue)
		{
			expr = new ConstantExpression(new Double( ((DoubleValue) val).value() ));
		}
		else if (val instanceof StringReference)
		{
			StringReference valStr = (StringReference) val;
			
			expr = new ConstantExpression(valStr.value());
		}
		else if (val instanceof ArrayReference)
		{
			ArrayReference valArray = (ArrayReference) val;
			
			ArrayType valArrType = (ArrayType) valArray.type();
		
			try
			{
				expr = new HeapReferenceExpression(valArray.uniqueID(), Utils.createArrayObjectDescriptor(valArrType.componentType().name(), valArray.length())); 
			}
			catch (com.sun.jdi.ClassNotLoadedException ex)
			{
				System.err.println("[ERROR] JDI internal exception (component type not ready): array type = " + valArrType.name());
			}
		}
		else if (val instanceof ObjectReference)
		{
			ObjectReference valObj = (ObjectReference) val;

			expr = new HeapReferenceExpression(valObj.uniqueID(), valObj.type().name());
		}
		
		// null value is used for methods that return nothing (i.e., void)
		
		// save into the cache
		value2SymbExpr.put(val, expr);

		return expr;
	}
}
