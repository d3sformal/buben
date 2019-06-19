package cz.cuni.mff.d3s.buben.examples.list1;

public class SimpleLinkedList
{
	private Entry head;
	private Entry last;

	public SimpleLinkedList()
	{
		head = null;
		last = null;
	}

	public void add(int val)
	{
		Entry e = new Entry();
		e.value = val;
		e.next = null;

		if (head == null)
		{
			head = e;
			last = e;
		}
		else
		{
			last.next = e;
			last = e;
		}
	}

	public IntIterator iterator()
	{
		return new IntIterator()
		{
			private Entry cur = head;
			private Entry old = null;

			public boolean hasNext()
			{
				return (cur != null);
			}

			public int next()
			{
				int val = cur.value;
				old = cur;
				cur = cur.next;
				return val;
			}

			public void remove()
			{
				if (old == head)
				{
					head = old.next;
					if (last == old) last = old.next;
				}
				else if (old == last)
				{
					Entry tmp = head;
					while (tmp.next != last) tmp = tmp.next;
					last = tmp;
					last.next = null;
					return;
				}
				else
				{
					Entry tmp = head;
					while (tmp.next != old) tmp = tmp.next;
					tmp.next = old.next;
				}
			}
		};
	}

	static class Entry
	{
		public int value;
		public Entry next;
	}
}
