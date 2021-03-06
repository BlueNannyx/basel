package ch.ethz.matsim.basel.population;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.ActivityFacilityImpl;
import org.matsim.facilities.ActivityOptionImpl;
import org.matsim.facilities.FacilitiesReaderMatsimV1;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

public class BaselPopConvert {
	private static Scenario scenario;
	private static Network network;
	private static Population population;
	private static PopulationFactory populationFactory;
	
	final static String popPath = "resources/population/";
	final static String networkPath = "resources/network/";
	final static String facilitiesPath = "resources/facilities/";
	final static String SYNTH_POP = popPath + "input/PlansFile2_corrected_sample.csv";
	final static String NETWORK = networkPath + "output/Mapped_basel_IVT_network.xml.gz";
	final static String FACILITIES = facilitiesPath + "output/facilities.xml.gz";
	final static String OUTPUT_PLANS = popPath + "output/population.xml.gz";
	final static String OUTPUT_ATTRIBUTES = popPath + "output/population_attributes.xml.gz";
	final static CoordinateTransformation TRANSFORMATION = 
			TransformationFactory.getCoordinateTransformation("WGS84", "CH1903_LV03_Plus");
	
	private Random random = new Random(3838494);

	private QuadTree<ActivityFacility> shopFacilitiesTree;
	private QuadTree<ActivityFacility> leisureFacilitiesTree;
	private QuadTree<ActivityFacility> educationFacilitiesTree;
	
	protected final static Logger log = Logger.getLogger(BaselPopConvert.class);
	
	public static void main(String[] args) {
		// Generates population based on input table
		BaselPopConvert popGen = new BaselPopConvert();
		popGen.populationCreation();
		new PopulationWriter(getScenario().getPopulation(), getScenario().getNetwork()).write(OUTPUT_PLANS);
		new ObjectAttributesXmlWriter(getScenario().getPopulation().getPersonAttributes()).writeFile(OUTPUT_ATTRIBUTES);
		//new FacilitiesWriter(getScenario().getActivityFacilities()).write(OUTPUT_FACILITIES);
	}
	
	public BaselPopConvert() {
		this.init();
	}
	
	private void init() {		
		/*
		 * Create the scenario
		 */
		Config config = ConfigUtils.createConfig();
		setScenario(ScenarioUtils.createScenario(config));
		network = getScenario().getNetwork();
		setPopulation(getScenario().getPopulation());
		setPopulationFactory(getPopulation().getFactory());
		/*
		 * Read the network and store it in the scenario
		 */
		new MatsimNetworkReader(network).readFile(NETWORK);
		/*
		 * Read the facilities and store them in the scenario
		 */
		new FacilitiesReaderMatsimV1(getScenario()).readFile(FACILITIES);
		/*
		 * Build quad trees for assigning home and work locations
		 */
		this.shopFacilitiesTree = this.createActivitiesTree("shop", getScenario()); 
		this.leisureFacilitiesTree = this.createActivitiesTree("leisure", getScenario()); 
		this.educationFacilitiesTree = this.createActivitiesTree("education", getScenario());		
	}
	
	private void populationCreation() {
		// set counter for user IDs
		int cnt = 1;
		int facId = getScenario().getActivityFacilities().getFacilities().size() + 100000;
		int noPrimFacID = 0;
		int facIDNotFound = 0;

		/*
		 * Read the census file Create the persons and add the
		 * socio-demographics
		 */
		try {
			BufferedReader bufferedReader = new BufferedReader(new FileReader(SYNTH_POP));
			String line = bufferedReader.readLine(); // skip header
			
			int index_HHCoordX = 20;
			int index_HHCoordY = 21;
			int index_PrimActFacilityID = 22;
			int index_TripChain = 9;
			int index_start_time = 10;
			int index_duration = 11;
			int index_mode = 12;
			int index_country = 0;
			int index_age = 2;
			int index_HHZoneType = 1;
			int index_sex = 3;
			int index_HHPersonsCount = 4;
			int index_DriversLicense = 5;
			int index_CarAvailability = 6;
			int index_PrimActZoneType = 14;
			

			while ((line = bufferedReader.readLine()) != null) {
				if(line.substring(line.length() - 1).equals(",")) line += "NA";
				String parts[] = line.split(",");
				
				/*
				 * Create and add home facility 
				 */
				Coord coord = TRANSFORMATION.transform(new Coord(Double.parseDouble(parts[index_HHCoordX]), Double.parseDouble(parts[index_HHCoordY])));
				Id<ActivityFacility> homeFacilityID = Id.create(String.format("%06d", facId), ActivityFacility.class);
				ActivityFacilityImpl homeFacility = (ActivityFacilityImpl)getScenario().getActivityFacilities().getFactory().createActivityFacility(homeFacilityID, coord);
				homeFacility.addActivityOption(new ActivityOptionImpl("home"));
				getScenario().getActivityFacilities().addActivityFacility(homeFacility);
				
				/*
				 * Create a person and add it to the population
				 */
				Person person = getPopulationFactory().createPerson(Id.create(String.format("%06d", cnt), Person.class));
				getPopulation().addPerson(person);
				
				Plan plan = getPopulationFactory().createPlan();
				person.addPlan(plan);
				person.setSelectedPlan(plan);
				
				Id<ActivityFacility> primActFacilityID = null;
				if(parts[index_PrimActZoneType].equals("outside")) { // assigning all out of boundaries primary activities to the same location
					primActFacilityID = Id.create("074040", ActivityFacility.class);
				}
				else if(!parts[index_PrimActFacilityID].equals("NA")) {
					primActFacilityID = Id.create(String.format("%06d", Integer.parseInt(parts[index_PrimActFacilityID])), ActivityFacility.class);
				}
				
				if((parts[index_TripChain].contains("W") || parts[index_TripChain].contains("E")) && primActFacilityID == null) {
					log.warn("Missing primary activity facilityID to worker or student. Person ID: " + person.getId().toString());
					getPopulation().removePerson(person.getId());
					noPrimFacID++;
				} else if((parts[index_TripChain].contains("W") || parts[index_TripChain].contains("E")) && 
						getScenario().getActivityFacilities().getFacilities().get(primActFacilityID) == null) {
					log.warn("Facility not found issue. Facility ID: " + (primActFacilityID != null ? primActFacilityID.toString() : "NULL"));
					getPopulation().removePerson(person.getId());
					facIDNotFound++;
				} else {
					createAndAddActivities(person, homeFacilityID, primActFacilityID, parts[index_TripChain],
							parts[index_start_time], parts[index_duration], parts[index_mode]);
					
					getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), "country", parts[index_country]);
					getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), "age", parts[index_age]);
					getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), "HHZoneType", parts[index_HHZoneType]);
					getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), "sex", parts[index_sex]);
					getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), "HHPersonsCount", parts[index_HHPersonsCount]);
					getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), "DriversLicense", parts[index_DriversLicense]);
					getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), "CarAvailability", parts[index_CarAvailability]);
					getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), "PrimActZoneType", parts[index_PrimActZoneType]);
				}
				
				cnt++;
				facId++;
			}
			
			bufferedReader.close();
			
			for(Person p : scenario.getPopulation().getPersons().values()){
				System.out.println(p.getId().toString());
			}
			
		} // end try
		catch (IOException e) {
			e.printStackTrace();
		}
		log.info("Total dismissed persons due to lacking facilityID issue: " + noPrimFacID);
		log.info("Total dismissed persons due to facility not found issue: " + facIDNotFound);
	}
	
	private void createAndAddActivities(Person person, Id<ActivityFacility> homeFacilityID,
			Id<ActivityFacility> primActFacilityID, String tripChain, String startTimes, String durations, String modes) {
		String tChain[] = tripChain.split("-");
		String sTimes[] = startTimes.split("-");
		String dur[] = durations.split("-");
		String mode[] = modes.split("-");
		Coord emptyCoord = new Coord(0, 0);
		
		Plan plan = person.getSelectedPlan();
		Activity previousActivity = null;
		boolean employed = false;
		
		for (int i = 0; i < tChain.length; i++){
			if (tChain[i].equals("H")) {
				Coord coord = getScenario().getActivityFacilities().getFacilities().get(homeFacilityID).getCoord();
				Activity home = getPopulationFactory().createActivityFromCoord("h", coord);
				home.setFacilityId(homeFacilityID);
				if(i > 0) home.setStartTime(Integer.parseInt(sTimes[i])); // doesn't add start time to the first home activity
				if(i < tChain.length -1){
					home.setEndTime(Integer.parseInt(sTimes[i])+Integer.parseInt(dur[i])); //home.setMaximumDuration(Integer.parseInt(dur[i])); // doesnt adds duration to last home activity
					if(home.getEndTime() - home.getStartTime() <= 0) home.setEndTime(home.getStartTime() + 60);
				} 
				plan.addActivity(home);
				previousActivity = home;
			}
			else if (tChain[i].equals("W")) {
				Coord coord = getScenario().getActivityFacilities().getFacilities().get(primActFacilityID).getCoord();
				Activity work = getPopulationFactory().createActivityFromCoord("w", coord);
				work.setFacilityId(primActFacilityID);
				work.setStartTime(Integer.parseInt(sTimes[i]));
				work.setEndTime(Integer.parseInt(sTimes[i])+Integer.parseInt(dur[i]));
//				work.setMaximumDuration(Integer.parseInt(dur[i]));
				if(work.getEndTime() - work.getStartTime() <= 0) work.setEndTime(work.getStartTime() + 60);
				plan.addActivity(work);
				employed = true;
				previousActivity = work;
			}
			else if (tChain[i].equals("E")) {
				Coord coord = getScenario().getActivityFacilities().getFacilities().get(primActFacilityID).getCoord();
				Activity education = getPopulationFactory().createActivityFromCoord("e", coord);
				if (employed) education.setFacilityId(getRandomFacility(education, previousActivity.getCoord()).getId());
				else education.setFacilityId(primActFacilityID);
				education.setStartTime(Integer.parseInt(sTimes[i]));
				education.setEndTime(Integer.parseInt(sTimes[i])+Integer.parseInt(dur[i]));
//				education.setMaximumDuration(Integer.parseInt(dur[i]));
				if(education.getEndTime() - education.getStartTime() <= 0) education.setEndTime(education.getStartTime() + 60);
				plan.addActivity(education);
				previousActivity = education;
			}
			else if (tChain[i].equals("L")) {
				Activity leisure = getPopulationFactory().createActivityFromCoord("l", emptyCoord);
				leisure.setFacilityId(getRandomFacility(leisure, previousActivity.getCoord()).getId());
				leisure.setCoord(getScenario().getActivityFacilities().getFacilities().get(leisure.getFacilityId()).getCoord());
				leisure.setStartTime(Integer.parseInt(sTimes[i]));
				leisure.setEndTime(Integer.parseInt(sTimes[i])+Integer.parseInt(dur[i]));
//				leisure.setMaximumDuration(Integer.parseInt(dur[i]));
				if(leisure.getEndTime() - leisure.getStartTime() <= 0) leisure.setEndTime(leisure.getStartTime() + 60);
				plan.addActivity(leisure);
				previousActivity = leisure;
			}
			else { // shop or sonstiges (S, T)
				Activity shop = getPopulationFactory().createActivityFromCoord("s", emptyCoord);
				shop.setFacilityId(getRandomFacility(shop, previousActivity.getCoord()).getId());
				shop.setCoord(getScenario().getActivityFacilities().getFacilities().get(shop.getFacilityId()).getCoord());
				shop.setStartTime(Integer.parseInt(sTimes[i]));
				shop.setEndTime(Integer.parseInt(sTimes[i])+Integer.parseInt(dur[i]));
//				shop.setMaximumDuration(Integer.parseInt(dur[i]));
				if(shop.getEndTime() - shop.getStartTime() <= 0) shop.setEndTime(shop.getStartTime() + 60);
				plan.addActivity(shop);
				previousActivity = shop;
			}
			
			// Add leg if it's not the last activity
			if(i < tChain.length -1){
				Leg leg = getPopulationFactory().createLeg(mode[i]);
				plan.addLeg(leg);
			}
		}
		
	}
	
	public QuadTree<ActivityFacility> createActivitiesTree(String activityType, Scenario scenario) {
		QuadTree<ActivityFacility> facQuadTree;
		
		facQuadTree = this.buildFacQuadTree(activityType, scenario.getActivityFacilities().getFacilitiesForActivityType(activityType));	
		
		return facQuadTree;
	}

	private QuadTree<ActivityFacility> buildFacQuadTree(String type, Map<Id<ActivityFacility>, ? extends ActivityFacility> map) {
		log.info(" building " + type + " facility quad tree");
		double minx = Double.POSITIVE_INFINITY;
		double miny = Double.POSITIVE_INFINITY;
		double maxx = Double.NEGATIVE_INFINITY;
		double maxy = Double.NEGATIVE_INFINITY;
	
		for (final ActivityFacility f : map.values()) {
			if (f.getCoord().getX() < minx) { minx = f.getCoord().getX(); }
			if (f.getCoord().getY() < miny) { miny = f.getCoord().getY(); }
			if (f.getCoord().getX() > maxx) { maxx = f.getCoord().getX(); }
			if (f.getCoord().getY() > maxy) { maxy = f.getCoord().getY(); }
		}
		minx -= 1.0;
		miny -= 1.0;
		maxx += 1.0;
		maxy += 1.0;
		System.out.println("        xrange(" + minx + "," + maxx + "); yrange(" + miny + "," + maxy + ")");
		QuadTree<ActivityFacility> quadtree = new QuadTree<ActivityFacility>(minx, miny, maxx, maxy);
		for (final ActivityFacility f : map.values()) {
			quadtree.put(f.getCoord().getX(),f.getCoord().getY(),f);
		}
		log.info("Quadtree size: " + quadtree.size());
		return quadtree;
	}	
	
	private ActivityFacility getRandomFacility(Activity activity, Coord coordPreviousActivity) {		
		double xCoordCenter = coordPreviousActivity.getX();
		double yCoordCenter = coordPreviousActivity.getY();
		ArrayList<ActivityFacility> facilities = new ArrayList<ActivityFacility>();
		
		if (activity.getType().startsWith("l")) {
			double radius = 8000.0;
			while (facilities.size() == 0) {
				facilities = (ArrayList<ActivityFacility>) this.leisureFacilitiesTree.getDisk(xCoordCenter, yCoordCenter, radius);
				radius *= 1.2;
			}
		}
		else if (activity.getType().startsWith("e")){
			double radius = 3000.0;
			while (facilities.size() == 0) {
				facilities = (ArrayList<ActivityFacility>) this.educationFacilitiesTree.getDisk(xCoordCenter, yCoordCenter, radius);
				radius *= 1.2;
			}
		}
		
		else { // shop or sonstiges
			double radius = 4000.0;
			while (facilities.size() == 0) {
				facilities = (ArrayList<ActivityFacility>) this.shopFacilitiesTree.getDisk(xCoordCenter, yCoordCenter, radius);
				radius *= 1.2;
			}
		}
		int randomIndex = (int)(random.nextFloat() * (facilities.size()));
		return facilities.get(randomIndex);
	}

	public static PopulationFactory getPopulationFactory() {
		return populationFactory;
	}

	public static void setPopulationFactory(PopulationFactory populationFactory) {
		BaselPopConvert.populationFactory = populationFactory;
	}

	public static Population getPopulation() {
		return population;
	}

	public static void setPopulation(Population population) {
		BaselPopConvert.population = population;
	}

	public static Scenario getScenario() {
		return scenario;
	}

	public static void setScenario(Scenario scenario) {
		BaselPopConvert.scenario = scenario;
	}
	

}
