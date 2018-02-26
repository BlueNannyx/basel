/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.ethz.matsim.basel.osm;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.pt2matsim.run.*;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;

import java.io.File;
import java.io.IOException;

/**
 * Usage test of PT2MATSim to document how the package can be used.
 *
 * @author boescpa
 */
public class PT2MATSim2 {

	private final static String input = "TestResources/TestsIVT2018/";
	private final static String output = "TestResources/TestsIVT2018/";

	public void prepareTests() {
		// Create output folder if not existing:
		new File(output + "GRTScheduleShapes/").mkdirs();
	}

	// To use the PT2MATSim Package, several steps are required:
	public static void main(String args[]) {
		// 1. Convert a gtfs schedule to an unmapped transit schedule
		//gtfsToSchedule();
			// OR a hafas schedule to an unmapped transit schedule
			//hafasToSchedule();
			// OR an osm file to an unmapped transit schedule
			//osmToSchedule();
		// 2. Convert an osm map to a network
		//osmToNetwork();
		// 3. Map the schedule onto the network
		mapScheduleToNetwork();
		// 4. Clean the network to eliminate unused links and nodes
//		cleanNetwork();
		// 5. Do a plausibility check
		checkPlausibility();
	}

	/**
	 * 	1. A GTFS or HAFAS Schedule or a OSM map with information on public transport
	 * 	has to be converted to an unmapped MATSim Transit Schedule.
	 *
	 * 	Here as a first example, the GTFS-schedule of GrandRiverTransit, Waterloo-Area, Canada, is converted.
	 */
	
	public void gtfsToSchedule() {
		String[] gtfsConverterArgs = new String[]{
				// [0] folder where the gtfs files are located (a single zip file is not supported)
				input + "GrandRiverTransit-GTFS/",
				// [1] which service ids should be used. One of the following:
				//		dayWithMostServices, date in the format yyyymmdd, dayWithMostTrips, all
				"dayWithMostServices",
				// [2] the output coordinate system. Use WGS84 for no transformation.
				"WGS84",
				// [3] output transit schedule file
				output + "UnmappedGRTSchedule.xml.gz",
				// [4] output default vehicles file (optional)
				output + "Vehicles.xml",
				// [5] output converted shape files. Is created based on shapes.txt and shows all trips
				// 		contained in the schedule. (optional)
				output + "GRTScheduleShapes/Shapes.shp"
		};
		Gtfs2TransitSchedule.main(gtfsConverterArgs);
	}
	// Here as a second example, the HAFAS-schedule of the BrienzRothornBahn, Switzerland, is
	// converted.
	
	public void hafasToSchedule() {
		String[] hafasConverterArgs = new String[]{
				// [0] hafasFolder
				input + "BrienzRothornBahn-HAFAS/",
				// [1] outputCoordinateSystem
				"WGS84",
				// [2] outputScheduleFile
				output + "UnmappedBRBSchedule.xml.gz",
				// [3] outputVehicleFile
				output + "BRBVehicles.xml"
		};
		try {
			Hafas2TransitSchedule.main(hafasConverterArgs);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	// And as a third example, the OSM-map of the Waterloo City Centre, Canada, is
	// converted.
	
	public void osmToSchedule() {
		String[] osmConverterArgs = new String[]{
				// [0] osm file
				input + "WaterlooCityCentre.osm",
				// [1] output schedule file
				output + "UnmappedOSMSchedule.xml.gz",
				// [2] output coordinate system (optional)
				"WGS84"
		};
		Osm2TransitSchedule.main(osmConverterArgs);
	}

	/**
	 * 2. A MATSim network of the area is required. If no such network is already available,
	 * the PT2MATSim package provides the possibility to use OSM-maps as data-input.
	 *
	 * Here as an example, the OSM-extract of the city centre of Waterloo, Canada, is converted.
	 */
	
	public static void osmToNetwork() {
		// Create an osmToNetwork-Config:
		CreateDefaultOsmConfig.main(new String[]{output + "OsmConverterConfig.xml"});
		// Open the osmToNetwork-Config and set the parameters to the required values
		// (usually done manually by opening the config with a simple editor)
		Config osmConverterConfig = ConfigUtils.loadConfig(
				output + "OsmConverterConfig.xml",
				OsmConverterConfigGroup.createDefaultConfig());
		osmConverterConfig.getModule("OsmConverter").addParam("osmFile", input + "Cambridge-KitchenerCA.osm");
		osmConverterConfig.getModule("OsmConverter").addParam("outputCoordinateSystem", "EPSG:32617");
		osmConverterConfig.getModule("OsmConverter").addParam("outputNetworkFile", output + "Cambridge-KitchenerCA.xml.gz");
		osmConverterConfig.getModule("OsmConverter").addParam("keepPaths", "false");
		// Save the osmToNetwork-Config
		// (usually done manually)
		new ConfigWriter(osmConverterConfig).write(output + "OsmConverterConfigAdjusted.xml");

		// Convert the OSM-file to a MATSim-network using the config
		Osm2MultimodalNetwork.main(new String[]{output + "OsmConverterConfigAdjusted.xml"});
	}

	/**
	 * 	3. The core of the PT2MATSim-package is the mapping process of the schedule to the network.
	 *
	 * 	Here as an example, the unmapped schedule of GrandRiverTransit (previously converted from GTFS) is mapped
	 * 	to the converted OSM network of the Waterloo Area, Canada.
	 */
	
	public static void mapScheduleToNetwork() {
		// Create a mapping config:
		CreateDefaultPTMapperConfig.main(new String[]{output + "MapperConfig.xml"});
		// Open the mapping config and set the parameters to the required values
		// (usually done manually by opening the config with a simple editor)
		Config mapperConfig = ConfigUtils.loadConfig(
				output + "MapperConfig.xml",
				PublicTransitMappingConfigGroup.createDefaultConfig());
		mapperConfig.getModule("PublicTransitMapping").addParam("inputNetworkFile", output + "basel_network_IVT.xml.gz");
		mapperConfig.getModule("PublicTransitMapping").addParam("outputNetworkFile", output + "basel_network_IVT_mapped.xml");
		mapperConfig.getModule("PublicTransitMapping").addParam("outputScheduleFile", output + "basel_schedule_IVT_mapped.xml");
		mapperConfig.getModule("PublicTransitMapping").addParam("outputStreetNetworkFile", output + "basel_network_IVT_mapped_StreetOnly.xml");
		mapperConfig.getModule("PublicTransitMapping").addParam("inputScheduleFile", input + "GVMB_Unmapped_Schedule.xml");
		mapperConfig.getModule("PublicTransitMapping").addParam("scheduleFreespeedModes", "rail, light_rail");
		mapperConfig.getModule("PublicTransitMapping").addParam("candidateDistanceMultiplier", "3.2"); 
		mapperConfig.getModule("PublicTransitMapping").addParam("maxTravelCostFactor", "10.0"); 
		mapperConfig.getModule("PublicTransitMapping").addParam("nLinkThreshold", "10");
		// Save the mapping config
		// (usually done manually)
		new ConfigWriter(mapperConfig).write(output + "MapperConfigAdjusted.xml");

		// Map the schedule to the network using the config
		PublicTransitMapper.main(new String[]{output + "MapperConfigAdjusted.xml"});
	}
	
	
//	public static void cleanNetwork() {
//		CleanNetwork CN = new CleanNetwork(output + "basel_network_IVT_mapped.xml", output + "Mapped_Basel_Schedule.xml");
//		CN.run();
//	}
//	
	/**
	 * 	4. The PT2MATSim package provides a plausibility checker to get quick feedback on the mapping process.
	 *
	 * 	Here as an example, the mapped transit schedule and the multimodal network created in step 3 is
	 * 	checked for plausibility.
	 */
	
	public static void checkPlausibility() {
		CheckMappedSchedulePlausibility.run(
				output + "basel_schedule_IVT_mapped.xml",
				output + "basel_network_IVT_mapped.xml",
				"EPSG:2056", 
				output + "PlausibilityResultsBasel/"
		);
	}

}