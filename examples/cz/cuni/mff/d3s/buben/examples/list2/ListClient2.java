package cz.cuni.mff.d3s.buben.examples.list2;

public class ListClient2
{
	public static void main(String[] args)
	{
		SimpleArrayList sal = new SimpleArrayList();

		sal.add(1);
		sal.add(2);
		sal.add(3);
		sal.add(5);
		sal.add(7);

		Result r = process(sal);

		System.out.println("count = " + r.elemCount + ", sum = " + r.valueSum);
		
		sal.remove(3);
		sal.remove(1);

		System.out.println("element = " + sal.get(1));
	}

	public static Result process(SimpleArrayList sal)
	{
		int count = 0;
		int sum = 0;

		IntIterator it = sal.iterator();
		while (it.hasNext())
		{
			int val = it.next();
			count++;
			sum = sum + val;
		}

		return new Result(count, sum);
	}

	static class Result
	{
		public int elemCount;
		public int valueSum;

		public Result(int ec, int vs)
		{
			elemCount = ec;
			valueSum = vs;
		}
	}
}
