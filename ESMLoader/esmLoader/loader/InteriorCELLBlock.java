package esmLoader.loader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

import tools.io.ESMByteConvert;
import esmLoader.common.PluginException;
import esmLoader.common.data.plugin.PluginGroup;

public class InteriorCELLBlock extends PluginGroup
{
	public InteriorCELLBlock(byte[] prefix)
	{
		super(prefix);
	}

	public void loadAndIndex(String fileName, RandomAccessFile in, int groupLength, Map<Integer, CELLPointer> interiorCELLByFormId)
			throws IOException, PluginException
	{
		int dataLength = groupLength;
		byte prefix[] = new byte[headerByteCount];

		while (dataLength >= headerByteCount)
		{
			int count = in.read(prefix);
			if (count != headerByteCount)
				throw new PluginException(fileName + ": Record prefix is incomplete");
			dataLength -= headerByteCount;
			String type = new String(prefix, 0, 4);
			int length = ESMByteConvert.extractInt(prefix, 4);

			if (type.equals("GRUP"))
			{
				length -= headerByteCount;
				int subGroupType = prefix[12] & 0xff;

				if (subGroupType == PluginGroup.INTERIOR_SUBBLOCK)
				{
					InteriorCELLSubblock children = new InteriorCELLSubblock(prefix);
					children.loadAndIndex(fileName, in, length, interiorCELLByFormId);
				}
				else
				{
					System.out.println("Group Type " + subGroupType + " not allowed as child of Int CELL block group");
				}
			}
			else
			{
				System.out.println("What the hell is a type " + type + " doing in the Int CELL block group?");
			}

			//prep for next iter
			dataLength -= length;
		}

		if (dataLength != 0)
		{
			if (getGroupType() == 0)
				throw new PluginException(fileName + ": Group " + getGroupRecordType() + " is incomplete");
			else
				throw new PluginException(fileName + ": Subgroup type " + getGroupType() + " is incomplete");
		}

	}
}
