package esmmanager.tes3;

/**
 * For making up fake children groups for tes3
 * @author phil
 *
 */
public class PluginGroup extends esmmanager.common.data.plugin.PluginGroup
{
	public PluginGroup(int groupType)
	{
		this.recordType = "GRUP";
		this.groupType = groupType;
		groupParentID = -1;
		groupLabel = new byte[4];
	}

}