package esmmanager.tes3;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import esmmanager.common.PluginException;
import esmmanager.common.data.plugin.PluginSubrecord;
import tools.io.ESMByteConvert;

// so land cells are in order with each land after the previous CELL
// typeToFormIdMap appears to be only used by the interior CELL system

// so in order to index the whole match I need to pull the first 2 subs out only
// then later pull the rest

public class CELLPluginGroup extends PluginGroup
{
	private int recordSize;

	public boolean isExterior = false;

	private boolean isLoaded = false;

	public int cellX = -999;
	public int cellY = -999;

	private PluginGroup temps = new PluginGroup(CELL_TEMPORARY);

	private PluginGroup dists = new PluginGroup(CELL_DISTANT);

	/**
	 * This just pulls the first 2 records out (NAME or INTV and then DATA)
	 * Then continues if an exterior cell to pull the following LAND record out as well
	 * @param fileName
	 * @param in
	 * @throws PluginException
	 * @throws IOException
	 */
	public CELLPluginGroup(byte[] prefix, RandomAccessFile in) throws PluginException, IOException
	{
		super(CELL);

		formID = Master.getNextFormId();

		getRecordList().add(temps);
		getRecordList().add(dists);

		recordType = new String(prefix, 0, 4);
		recordSize = ESMByteConvert.extractInt(prefix, 4);
		unknownInt = ESMByteConvert.extractInt(prefix, 8);
		recordFlags1 = ESMByteConvert.extractInt(prefix, 12);

		//we don't allocate or read the full record data now, we just need the first 2
		//recordData = new byte[recordSize];

		filePositionPointer = in.getFilePointer();

		byte[] subrecordDataHead = new byte[8];

		// read off the first header
		int count = in.read(subrecordDataHead);
		if (count != 8)
			throw new PluginException("" + recordType + " record bad length, asked for " + 8 + " got " + count);

		// subrecordType1 should be "CELL"
		//String subrecordType1 = new String(subrecordDataHead, 0, 4);
		int subrecordLength = ESMByteConvert.extractInt(subrecordDataHead, 4);
		byte[] subrecordData1 = new byte[subrecordLength];
		count = in.read(subrecordData1);
		if (count != subrecordLength)
			throw new PluginException("" + recordType + " record bad length, asked for " + subrecordLength + " got " + count);

		// read off the second header
		count = in.read(subrecordDataHead);
		if (count != 8)
			throw new PluginException("" + recordType + " record bad length, asked for " + 8 + " got " + count);

		// subrecordType2 should be "DATA"
		//String subrecordType2 = new String(subrecordDataHead, 0, 4);
		subrecordLength = ESMByteConvert.extractInt(subrecordDataHead, 4);
		byte[] subrecordData2 = new byte[subrecordLength];
		count = in.read(subrecordData2);
		if (count != subrecordLength)
			throw new PluginException("" + recordType + " record bad length, asked for " + subrecordLength + " got " + count);

		if (recordType.equals("CELL"))
		{
			byte[] DATA = subrecordData2;
			int flags = ESMByteConvert.extractInt(DATA, 0);
			editorID = new String(subrecordData1).trim();

			// is it exterior
			isExterior = (flags & 0x1) == 0;
			if (isExterior)
			{
				cellX = ESMByteConvert.extractInt(DATA, 4);
				cellY = ESMByteConvert.extractInt(DATA, 8);
			}
		}
		else
		{
			new Throwable("what the hell! " + recordType).printStackTrace();
		}

		// now skip the rest of the data for now, until load time
		in.skipBytes(recordSize - (8 + subrecordData1.length + 8 + subrecordData2.length));
	}

	public boolean isLoaded()
	{
		return isLoaded;
	}

	/**
	 * This will load the record data fully and build the record properly
	 * It will also continue and load the LAND record that follows if this is an exterior cell
	 * @param in
	 * @throws PluginException 
	 * @throws IOException 
	 */
	public void load(RandomAccessFile in) throws PluginException, IOException
	{
		synchronized (in)
		{
			in.seek(filePositionPointer);
			recordData = new byte[recordSize];
			int count = in.read(recordData);
			if (count != recordSize)
				throw new PluginException("" + recordType + " record bad length, asked for " + recordSize + " got " + count);

			//NOTE!!! we do not use our parent as we are a newer type, must use tes3 style	
			subrecordList = new ArrayList<PluginSubrecord>();
			PluginRecord.getFillSubrecords(recordType, subrecordList, recordData);

			PluginRecord refr = null;
			for (int i = 0; i < subrecordList.size(); i++)
			{
				PluginSubrecord sub = subrecordList.get(i);
				if (sub.getSubrecordType().equals("FRMR"))
				{
					// have we finished a prior now?
					if (refr != null)
						temps.getRecordList().add(refr);
					//Note we must use unique record ids (not used anywhere to refer afaik)
					refr = new PluginRecord(Master.getNextFormId(), "REFR", "REFR:" + i);
				}

				// just chuck it in, if we are building up a refr now
				if (refr != null)
					refr.getSubrecords().add(sub);
			}

			// have we finished a prior now?
			if (refr != null)
				temps.getRecordList().add(refr);

			//If we are an exterior the next record is a LAND!
			if (isExterior)
			{
				byte[] prefix = new byte[16];
				count = in.read(prefix);
				if (count != 16)
					throw new PluginException(": record prefix is incomplete");

				// must have an id too
				PluginRecord record = new PluginRecord(Master.getNextFormId(), prefix);
				record.load("", in);
				if (record.getRecordType().equals("LAND"))
				{
					temps.getRecordList().add(record);
					dists.getRecordList().add(record);
				}
				else
				{
					new Throwable("Record following exterior cell is not LAND!").printStackTrace();
				}
			}
		}
		isLoaded = true;
	}

	public PluginRecord createPluginRecord()
	{
		PluginRecord pr = new PluginRecord(getFormID(), "CELL", getEditorID());

		List<PluginSubrecord> subrecords = getSubrecords();
		for (int i = 0; i < subrecords.size() && i < 6; i++)
		{
			PluginSubrecord sr = subrecords.get(i);
			if (sr.getSubrecordType().equals("NAME") || sr.getSubrecordType().equals("DATA") //
					|| sr.getSubrecordType().equals("RGNN") || sr.getSubrecordType().equals("NAM0") //
					|| sr.getSubrecordType().equals("NAM5") || sr.getSubrecordType().equals("WHGT") //
					|| sr.getSubrecordType().equals("AMBI"))
				pr.getSubrecords().add(sr);
		}
		return pr;
	}

	public String toString()
	{
		return "CELLPluginGroup exterior=" + this.isExterior + " " + this.editorID + " x " + this.cellX + " y " + this.cellY;
	}
}
