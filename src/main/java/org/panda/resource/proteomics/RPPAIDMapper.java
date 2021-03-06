package org.panda.resource.proteomics;

import org.panda.resource.FileServer;
import org.panda.resource.HGNC;
import org.panda.utility.ArrayUtil;
import org.panda.utility.FileUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * For mapping RPPA IDs from MDAnderson to something that does not change every time.
 * @author Ozgun Babur
 */
public class RPPAIDMapper extends FileServer
{
	/**
	 * Alternative ID to standard ID.
	 */
	private Map<String, String> altToID;

	private Map<String, String> idToPlatformLine;

	private static RPPAIDMapper instance;

	private static final String RESOURCE_FILE = "MDACC-RPPA-ID-guide.txt";

	public static RPPAIDMapper get()
	{
		if (instance == null) instance = new RPPAIDMapper();
		return instance;
	}

	public String getPreferredID(String anID)
	{
		if (anID == null || anID.isEmpty()) return null;
		return altToID.get(anID);
	}

	public List<String> writePlatformForIDs(Collection<String> ids, String outFile) throws IOException
	{
		List<String> notFound = new ArrayList<>();

		BufferedWriter writer = Files.newBufferedWriter(Paths.get(outFile));
		writer.write("ID\tSymbols\tSites\tEffect");

		ids.stream().sorted().forEach(id ->
		{
			String pref = getPreferredID(id);
			if (pref != null)
			{
				FileUtil.lnwrite(idToPlatformLine.get(pref), writer);
			}
			else
			{
				notFound.add(id);
			}
		});

		writer.close();
		return notFound;
	}

	public String[] getPlatformLine(String id)
	{
		String pid = getPreferredID(id);
		if (pid == null) throw new IllegalArgumentException("ID not recognized: " + id);

		return idToPlatformLine.get(pid).split("\t");
	}

	public String[] getConvertedIDs(String[] ids)
	{
		String[] c = new String[ids.length];

		for (int i = 0; i < c.length; i++)
		{
			c[i] = getPreferredID(ids[i]);
		}
		return c;
	}

	@Override
	public String[] getLocalFilenames()
	{
		return new String[]{RESOURCE_FILE};
	}

	@Override
	public boolean load() throws IOException
	{
		altToID = new HashMap<>();
		idToPlatformLine = new HashMap<>();
		getResourceAsStream(getLocalFilenames()[0]).skip(1).map(l -> l.split("\t")).forEach(t ->
		{
			Arrays.stream(t[1].split(" ")).forEach(alt -> altToID.put(alt, t[0]));
			idToPlatformLine.put(t[0], ArrayUtil.getString("\t", t[0], t[2],
				t.length > 3 ? t[3] : "", t.length > 4 ? t[4] : ""));
		});

		return true;
	}

	// Resource file initialization. Note that below code is written to run only once. After that, the resource file
	// should be maintained manually for further changes.

	private static void prepareResourceFileFromInitialMapping() throws IOException
	{
		String dir = "/home/babur/Documents/Analyses/SMMART/Patient1/RPPA/Sample2/";

		Map<String, String> idMap = new HashMap<>();
		Map<String, String> lineMap = new HashMap<>();

		Files.lines(Paths.get(dir + "platform.txt")).skip(1).map(l -> l.split("\t")).forEach(t ->
		{
			idMap.put(t[1], t[0]);
			String rest = t[2] + "\t";
			if (t.length > 3) rest += t[3];
			rest += "\t";
			if (t.length > 4) rest += t[4];
			lineMap.put(t[1], rest);
		});

		dir = "/home/babur/Projects/repo/resource-files/";
		BufferedWriter writer = Files.newBufferedWriter(Paths.get(dir + RESOURCE_FILE));
		writer.write("Preferred ID\tAll possible IDs\tSymbols\tSites\tEffect");

		idMap.keySet().stream().sorted().forEach(id ->
			FileUtil.lnwrite(id + "\t" + id + " " + idMap.get(id) + "\t" + lineMap.get(id), writer));

		writer.close();
	}

	public static void convertFromMDACCToCP(String mdaccFile, String cpOutFile) throws IOException
	{
		Map<String, Map<String, Double>> map = MDACCFormatRPPALoader.load(mdaccFile);
		writeAsCPFile(map, cpOutFile, null);
	}

	public static void writeAsCPFile(Map<String, Map<String, Double>> map, String outFile, List<String> sampleOrder) throws IOException
	{
		List<String> samples = sampleOrder == null ? new ArrayList<>(map.get(map.keySet().iterator().next()).keySet()) : sampleOrder;
		if (sampleOrder == null) samples.sort(String::compareTo);

		BufferedWriter writer = Files.newBufferedWriter(Paths.get(outFile));

		writer.write("ID\tSymbols\tSites\tEffect");

		samples.forEach(s -> FileUtil.tab_write(s, writer));

		map.keySet().stream().sorted().forEach(ab ->
		{
			String pref = get().getPreferredID(ab);

			if (pref == null) throw new RuntimeException("Encountered unrecognized antibody name: " + ab);

			FileUtil.lnwrite(get().idToPlatformLine.get(pref), writer);

			samples.forEach(s -> FileUtil.tab_write(map.get(ab).get(s), writer));
		});

		writer.close();
	}

	public static void main(String[] args) throws IOException
	{
//		prepareResourceFileFromInitialMapping();

		String dir = "/home/ozgun/Analyses/Alexey/";
		convertFromMDACCToCP(dir + "14_Alexey_Danilov__Taylor_Rowland_B.csv", dir + "data.txt");
	}
}
