package esmio.loader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import com.frostwire.util.SparseArray;

import esmio.common.PluginException;
import esmio.common.data.plugin.PluginGroup;
import tools.io.ESMByteConvert;

public class DIALTopGroup extends PluginGroup
{
	private SparseArray<CELLDIALPointer> DIALByFormID = null;

	public DIALTopGroup(byte[] prefix)
	{
		super(prefix);
	}

	public CELLDIALPointer getDIAL(int dialId) throws IOException, PluginException
	{
		return DIALByFormID.get(dialId);
	}

	public void getAllInteriorCELLFormIds(ArrayList<CELLDIALPointer> ret) throws IOException, PluginException
	{
		for (int i = 0; i < DIALByFormID.size(); i++)
			ret.add(DIALByFormID.get(DIALByFormID.keyAt(i)));
	}

	public void loadAndIndex(RandomAccessFile in, int groupLength) throws IOException, PluginException
	{
		DIALByFormID = new SparseArray<CELLDIALPointer>();
		int dataLength = groupLength;
		byte prefix[] = new byte[headerByteCount];

		CELLDIALPointer cellPointer = null;

		while (dataLength >= headerByteCount)
		{
			long filePositionPointer = in.getFilePointer();

			int count = in.read(prefix);
			if (count != headerByteCount)
				throw new PluginException(": Record prefix is incomplete");
			dataLength -= headerByteCount;
			String type = new String(prefix, 0, 4);
			int length = ESMByteConvert.extractInt(prefix, 4);

			if (type.equals("GRUP"))
			{
				length -= headerByteCount;
				int subGroupType = prefix[12] & 0xff;

				if (subGroupType == PluginGroup.TOPIC)
				{
					cellPointer.cellChildrenFilePointer = filePositionPointer;
					// now skip the group
					in.skipBytes(length);
				}
				else
				{
					System.out.println("Group Type " + subGroupType + " not allowed as child of DIAL group");
				}
			}
			else if (type.equals("DIAL"))
			{
				int formID = ESMByteConvert.extractInt(prefix, 12);
				cellPointer = new CELLDIALPointer(formID, filePositionPointer);
				DIALByFormID.put(formID, cellPointer);
				in.skipBytes(length);
			}
			else
			{
				System.out.println("What the hell is a type " + type + " doing in the Int CELL sub block group?");
			}

			//prep for next iter
			dataLength -= length;
		}

		if (dataLength != 0)
		{
			if (getGroupType() == 0)
				throw new PluginException(": Group " + getGroupRecordType() + " is incomplete");
			else
				throw new PluginException(": Subgroup type " + getGroupType() + " is incomplete");
		}

	}

}
