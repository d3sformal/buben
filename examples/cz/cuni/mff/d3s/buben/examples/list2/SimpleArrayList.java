package cz.cuni.mff.d3s.buben.examples.list2;

public class SimpleArrayList
{
	private int[] entries;
	private int size;

	public SimpleArrayList()
	{
		entries = new int[2];
		size = 0;
	}

	public void add(int val)
	{
		if (size == entries.length)
		{
			int[] newEntries = new int[size*2];
			System.arraycopy(entries, 0, newEntries, 0, size);
			entries = newEntries;
		}

		entries[size] = val;
		size++;
	}

	public int get(int index)
	{
		return entries[index];
	}

	public void remove(int index)
	{
		for (int i = index+1; i < size; i++)
		{
			entries[i-1] = entries[i];
		}
	}

	public IntIterator iterator()
	{
		return new IntIterator()
		{
			private int cur = 0;

			public boolean hasNext()
			{
				return (cur < size);
			}

			public int next()
			{
				int val = entries[cur];
				cur++;
				return val;
			}

			public void remove()
			{
				for (int i = cur+1; i < size; i++)
				{
					entries[i-1] = entries[i];
				}
			}
		};
	}
}
