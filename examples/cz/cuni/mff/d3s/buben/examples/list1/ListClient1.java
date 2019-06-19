package cz.cuni.mff.d3s.buben.examples.list1;

public class ListClient1
{
	public static void main(String[] args)
	{
		SimpleLinkedList sll = new SimpleLinkedList();

		sll.add(1);
		sll.add(4);
		sll.add(9);
		sll.add(16);

		Result r = process(sll);

		System.out.println("count = " + r.elemCount + ", sum = " + r.valueSum);
	}

	public static Result process(SimpleLinkedList sll)
	{
		int count = 0;
		int sum = 0;

		IntIterator it = sll.iterator();
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
