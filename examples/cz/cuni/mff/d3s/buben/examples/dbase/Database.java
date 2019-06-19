package cz.cuni.mff.d3s.buben.examples.dbase;

public class Database
{
	private int pos;
	private int[] values;

	public Database(int size)
	{
		pos = 0;
		values = new int[size];
	}

	public int loadNextInt()
	{
		if (pos >= values.length) pos = 0;

		int v = values[pos];
		pos++;

		return v;
	}

	public int[] loadNextBuf(int len)
	{
		int[] b = new int[len];
		
		for (int i = 0; i < len; i++)
		{
			if (pos >= values.length) pos = 0;

			b[i] = values[pos];
			pos++;
		}

		return b;
	}

	public void saveInt(int v)
	{
		values[pos] = v;
		pos++;
	}

	public void saveBuf(int[] a)
	{
		for (int i = 0; i < a.length; i++)
		{
			values[pos] = a[i];
			pos++;
		}
	}

	public void reset()
	{
		pos = 0;
	}

	public Info getInfo()
	{
		Info dbi = new Info();
		
		dbi.curSize = values.length;
		dbi.curPos = pos;

		return dbi;
	}

	static class Info
	{
		public int curSize;
		public int curPos;
	}
}

