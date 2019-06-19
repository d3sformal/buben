package cz.cuni.mff.d3s.buben.examples.dbase;

public class Client
{
	public static void main(String[] args)
	{
		Database db = new Database(32);

		db.saveInt(1);
		db.saveInt(2);

		db.saveInt(4);
		int[] a1 = new int[]{5,10,15,20};
		db.saveBuf(a1);

		db.reset();

		Database.Info dbInfo = db.getInfo();

		System.out.println("info: size = " + dbInfo.curSize + ", pos = " + dbInfo.curPos);

		int v1 = db.loadNextInt();
		db.loadNextInt();

		int sz1 = db.loadNextInt();
		int[] a2 = db.loadNextBuf(sz1);

		System.out.println("v1 = " + v1);
		System.out.println("a2 = " + a2);
	}
}
