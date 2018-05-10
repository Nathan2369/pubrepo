package popp1594;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;

import popp1594.GAChromosome;
import popp1594.GAPopulation;
import popp1594.KnowledgeCompendium;
import popp1594.AStarSearch;
import popp1594.FollowPathAction;
import popp1594.Graph;
import popp1594.Vertex;
import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.clients.TeamClient;
import spacesettlers.clients.examples.ExampleGAChromosome;
import spacesettlers.clients.examples.ExampleGAPopulation;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

/**
 * HarpoppBot is an AI that attempts to use the genetic algorithm to improve performance of a ship in the Space Settlers game.
 */
public class HarpoppBot extends TeamClient {
	KnowledgeCompendium myKnowledge;
	private GAChromosome currentPolicy;
	private GAPopulation population;
	private int populationSize = 10; //10
	private int startTurnResources = 0, lastResources = 0;
	private int resourcesCollected = 0;
	
	FollowPathAction followPathAction;
	HashMap <UUID, Graph> graphByShip;
	int timeSteps;
	int REPLAN_STEPS = 10;
	boolean movementFinished = true;
	Position target = null;
	int timeStart = 0;
	int timeEnd = 0;
	
	
	/**
	 * Professors method to initialize knowledge file, adapted to
	 * custom knowledge class "KnowledgeCompendium".
	 */
	@Override
	public void initialize(Toroidal2DPhysics space) {
		
		graphByShip = new HashMap<UUID, Graph>();
		timeSteps = 0;
		
		myKnowledge = new KnowledgeCompendium();
		
		//xstream initialized
		XStream xstream = new XStream();
		xstream.alias("GAPopulation", GAPopulation.class);

		//Location will be current directory, file name is botKnowledge.xml
		setKnowledgeFile(System.getProperty("user.dir") + "\\population.xml");

		try { 
			//load in population from file
			System.out.println("Population being loaded.");
			population = (GAPopulation) xstream.fromXML(new File(getKnowledgeFile()));
			population.printPopulation();
			population.printFitness();
			currentPolicy = population.getCurrentMember();
			 
		} catch (XStreamException e) {
			// if you get an error, handle it other than a null pointer because
			// the error will happen the first time you run
			System.out.println("New population being constructed.");
			population = new GAPopulation(populationSize);
			population.printPopulation();
			currentPolicy = population.getCurrentMember();
		}
	}

	/**
	 * Professors method to shut down knowledge file, adapted to
	 * custom knowledge class "KnowledgeCompendium".
	 */
	@Override
	public void shutDown(Toroidal2DPhysics space) {
		
		
	}

	/**
	 * Updates the knowledge file with list of bases & ships
	 */
	private void updateActionableObjectList(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects){
		
		//clear the knowledge file to eliminate objects which no longer exist.
		myKnowledge.clearBases();
		myKnowledge.clearShips();
		myKnowledge.clearAstroids();
		myKnowledge.clearBeacons();
		
		//update knowledge file of all bases & ships
		for (AbstractObject actionable : actionableObjects) {
			
			//If the object is a ship, check if it's in our ship list. If not, add it.
			if (actionable instanceof Ship) {
				if(!myKnowledge.getTeamShips().contains(actionable))
					myKnowledge.addTeamShip(actionable);
			}
			
			//If the object is a base, check if it's in our base list. If not, add it.
			if (actionable instanceof Base) {
				myKnowledge.addTeamBase(actionable);
			}
		}
		
		//save asteroids to the knowledge representation
		for (Asteroid asteroid : space.getAsteroids()){
			myKnowledge.addAsteroid(asteroid);
		}
		
		//save beacons to the knowledge representation
		for (Beacon beacon : space.getBeacons()){
			myKnowledge.addBeacon(beacon);
		}
	}
	
	/**
	 * Assign actions to all objects on our team
	 */
	@Override
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();
		updateActionableObjectList(space, actionableObjects);
		
		//Assign actions to all ships in the knowledge file
		for(Object ship : myKnowledge.getTeamShips()){
			if(timeSteps >= REPLAN_STEPS || timeSteps == 0)
			{
				timeStart = space.getCurrentTimestep();
				timeSteps = 0;
				movementFinished = false;
				target = getShipTargetPosition(space, actionableObjects, (Ship) ship);
				AbstractAction action = getAStarPathToGoal(space, (Ship) ship, target);
				actions.put(((Ship) ship).getId(), action);
			}
			else if(timeSteps >= REPLAN_STEPS)
			{
				timeSteps = 0;
				AbstractAction action = getAStarPathToGoal(space, (Ship) ship, target);
		
				actions.put(((Ship) ship).getId(), action);
			}
			else
				actions.put(((Ship) ship).getId(), ((Ship) ship).getCurrentAction());
		}
		
		//Assign actions to bases in the knowledge file
		for(Object base : myKnowledge.getTeamBases()){
			Base myBase = (Base) base;
			actions.put(myBase.getId(), new DoNothingAction());
		}
		timeSteps++;
		return actions;
	}

	@Override
	/**
	 * getTeamPurchases purchases ships or bases for the turn.
	 * We were not able to get purchasing ships working, so it is disabled.
	 */
	public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects, 
			ResourcePile resourcesAvailable, 
			PurchaseCosts purchaseCosts) {
		
		HashMap<UUID, PurchaseTypes> purchases = new HashMap<UUID, PurchaseTypes>();
		double BASE_BUYING_DISTANCE = 500;

		//Prioritizes buying a ship if the resources are available
		/*if (purchaseCosts.canAfford(PurchaseTypes.SHIP, resourcesAvailable)) {
			
			//Cycle through bases & buy ships if possible
			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Base) {
					Base base = (Base) actionableObject;
					
					purchases.put(base.getId(), PurchaseTypes.SHIP);
					break;
				}
			}
		}*/
		
		//Cycles through all ships & buys bases & places them
		if (purchaseCosts.canAfford(PurchaseTypes.BASE, resourcesAvailable)) {
			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Ship) {
					Ship ship = (Ship) actionableObject;
					Set<Base> bases = space.getBases();

					//ship can place a base once it is a certain distance from the base
					boolean buyBase = true;
					for (Base base : bases) {
						if (base.getTeamName().equalsIgnoreCase(getTeamName())) {
							double distance = space.findShortestDistance(ship.getPosition(), base.getPosition());
							if (distance < BASE_BUYING_DISTANCE) {
								buyBase = false;
							}
						}
					}
					if (buyBase) {
						purchases.put(ship.getId(), PurchaseTypes.BASE);
						break;
					}
				}
			}		
		} 	
		return purchases;	
	}

	/**
	 * Returns the position of the target the ship will now pursue
	 * The ship either pursues energy beacons, its base, asteroids, or sits still
	 */
	private Position getShipTargetPosition(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects, Ship ship) {
				
		startTurnResources = ship.getResources().getTotal();
		
		//If low on energy, try to obtain an energy beacon
		if (ship.getEnergy() < 2000){
			
			//set the target beacon as the closest beacon
			Beacon targetBeacon = selectNearestBeacon(space, ship);
			Position newPos = null;
			
			//if there isn't any beacon, wait for a beacon.
			if(targetBeacon == null) {
				//newPos = ship.getPosition();
			} else {
				newPos = targetBeacon.getPosition();
				return newPos;
			}
			
			//Ship is no longer aiming for base
			myKnowledge.mapShipAimingForBase(ship, false);
			
			
			
		//If ship has lots of resources, return to base.
		} else if(ship.getResources().getTotal() > 1500) {
			
			//set action to move to nearest base
			Base targetBase = selectNearestBase(space, ship);
			Position newPos = targetBase.getPosition();
			updateActionableObjectList(space, actionableObjects);
			//ship is now aiming towards the base
			myKnowledge.mapShipAimingForBase(ship, true);
			return newPos;
			
		//Professor's did-we-bounce-off-base code
		} else if (ship.getResources().getTotal() == 0 && ship.getEnergy() > 2000 && myKnowledge.aimingForBaseContains(ship) && myKnowledge.aimingForBase(ship)) {
			myKnowledge.mapShipAimingForBase(ship, false);

		//Otherwise, select an asteroid based on the policy from our genetic algorithm
		} else {			
			Asteroid ast = decideAsteroidFromPolicy(space, ship);
			if(ast == null)
				ast = pickNearestAsteroid(space, ship);

			//return the asteroid mining action
			return ast.getPosition();
		}
		
		return ship.getPosition();
		
	}
	
	/**
	 * Returns the nearest base to the ship from our knowledge representation
	 */
	private Base selectNearestBase(Toroidal2DPhysics space, Ship ship) {
		
		double minDistance = Double.MAX_VALUE;
		Base nearestBase = null;
		
		//search through all bases in knowledge file
		for(Object myBase : myKnowledge.getTeamBases()){
			Base base = (Base) myBase;
			if (base.getTeamName().equalsIgnoreCase(ship.getTeamName())){
							
				//find the distance between ship and the base
				double dist = space.findShortestDistance(ship.getPosition(), base.getPosition());
				
				//Ensures nearestBase is the base with the smallest distance
				if (dist < minDistance){
					minDistance = dist;
					nearestBase = base;
				}
			}
		}
		return nearestBase;
	}
	
	/**
	 * Selects the nearest beacon for pursuit from our knowledge representation
	 */
	private Beacon selectNearestBeacon(Toroidal2DPhysics space, Ship ship){
		
		//Set up values for search
		Beacon closestBeacon = null;
		double minDistance = Double.MAX_VALUE;
		
		//Find nearest beacon
		for(Beacon beacon : myKnowledge.getBeacons()){
			//find the distance between ship and the beacon
			double dist = space.findShortestDistance(ship.getPosition(), beacon.getPosition());
			
			//Ensures nearestBeacon is the beacon with the smallest distance
			if (dist < minDistance){
				minDistance = dist;
				closestBeacon = beacon;
			}
		}
		
		return closestBeacon;
	}
	
	/**
	 * Selects the closest free asteroid, instead of the highest value one.
	 */
	
	private Asteroid pickNearestAsteroid(Toroidal2DPhysics space, Ship ship){
		
		Set<Asteroid> asteroids = space.getAsteroids();
		double minDistance = Double.MAX_VALUE;
		
		Asteroid closestAsteroid = null;
		
		for(Asteroid asteroid : asteroids){
			//if a ship is not already assigned to that asteroid
			if(!myKnowledge.asteroidToShipContains(asteroid)){
				
				double dist = space.findShortestDistance(ship.getPosition(), asteroid.getPosition());
				
				//ensure asteroid is minable and closest
				if(asteroid.isMineable() && dist < minDistance){
					minDistance = dist;
					closestAsteroid = asteroid;
				}
			}
		}
		
		return closestAsteroid;
		
	}
	
	/*
	 * Picks the closest unobstructedAsteroid, did not work very well and is not currently used.
	 */
	private Asteroid pickNearestUnobstructedAsteroid(Toroidal2DPhysics space, Ship ship){
		
		Set<Asteroid> asteroids = space.getAsteroids();
		double minDistance = Double.MAX_VALUE;
		
		Asteroid closestAsteroid = null;
		
		for(Asteroid asteroid : asteroids){
			//if a ship is not already assigned to that asteroid
			if(!myKnowledge.asteroidToShipContains(asteroid)){
				
				double dist = space.findShortestDistance(ship.getPosition(), asteroid.getPosition());
				
				//ensure asteroid is minable and closest
				if(asteroid.isMineable() && dist < minDistance && space.isPathClearOfObstructions(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50)){
					minDistance = dist;
					closestAsteroid = asteroid;
				}
			}
		}
		return closestAsteroid;
	}
	
	/*
	 * Picks the nearest asteroid with a mineable asteroid in the way. Did not work very well and is not currently used.
	 */
	private Asteroid pickNearestObstructedByMineableAsteroid(Toroidal2DPhysics space, Ship ship){
		
		Set<Asteroid> asteroids = space.getAsteroids();
		double minDistance = Double.MAX_VALUE;
		
		Asteroid closestAsteroid = null;
		
		for(Asteroid asteroid : asteroids){
			//if a ship is not already assigned to that asteroid
			if(!myKnowledge.asteroidToShipContains(asteroid)){
				
				double dist = space.findShortestDistance(ship.getPosition(), asteroid.getPosition());
				
				//ensure asteroid is minable and closest
				if(asteroid.isMineable() && dist < minDistance && isPathObstructedByMineable(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50, space)){
					minDistance = dist;
					closestAsteroid = asteroid;
				}
			}
		}
		return closestAsteroid;
	}
	
	/*
	 * Picks the nearest asteroid with a beacon in the way. Did not work very well and is not currently used.
	 */
	private Asteroid pickNearestObstructedByBeaconAsteroid(Toroidal2DPhysics space, Ship ship){
		
		Set<Asteroid> asteroids = space.getAsteroids();
		double minDistance = Double.MAX_VALUE;
		
		Asteroid closestAsteroid = null;
		
		for(Asteroid asteroid : asteroids){
			//if a ship is not already assigned to that asteroid
			if(!myKnowledge.asteroidToShipContains(asteroid)){
				
				double dist = space.findShortestDistance(ship.getPosition(), asteroid.getPosition());
				
				//ensure asteroid is minable and closest
				if(asteroid.isMineable() && dist < minDistance && isPathObstructedByBeacon(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50, space)){
					minDistance = dist;
					closestAsteroid = asteroid;
				}
			}
		}
		return closestAsteroid;
	}
	
	
	
	/*
	 * Picks the furthest asteroid away. This causes huge problems for the ship and masssively affects the score. The genetic algorithm should mvoe away from ever picking this policy.
	 */
	private Asteroid pickFurthestAsteroid(Toroidal2DPhysics space, Ship ship){
		
		Set<Asteroid> asteroids = space.getAsteroids();
		double maxDistance = Double.MIN_VALUE;
		
		Asteroid furthestAsteroid = null;
		
		for(Asteroid asteroid : asteroids){
			//if a ship is not already assigned to that asteroid
			if(!myKnowledge.asteroidToShipContains(asteroid)){
				
				double dist = space.findShortestDistance(ship.getPosition(), asteroid.getPosition());
				
				//ensure asteroid is minable and closest
				if(asteroid.isMineable() && dist > maxDistance){
					maxDistance = dist;
					furthestAsteroid = asteroid;
				}
			}
		}
		
		return furthestAsteroid;
		
	}
	
	/*
	 * Similar to pick furthest, tries to find unobstructed
	 */
	private Asteroid pickFurthestUnobstructedAsteroid(Toroidal2DPhysics space, Ship ship){
		
		Set<Asteroid> asteroids = space.getAsteroids();
		double maxDistance = Double.MIN_VALUE;
		
		Asteroid furthestAsteroid = null;
		
		for(Asteroid asteroid : asteroids){
			//if a ship is not already assigned to that asteroid
			if(!myKnowledge.asteroidToShipContains(asteroid)){
				
				double dist = space.findShortestDistance(ship.getPosition(), asteroid.getPosition());
				
				//ensure asteroid is minable and closest
				if(asteroid.isMineable() && dist > maxDistance && space.isPathClearOfObstructions(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50)){
					maxDistance = dist;
					furthestAsteroid = asteroid;
				}
			}
		}
		return furthestAsteroid;	
	}
	
	private Asteroid pickFurthestObstructedByMineableAsteroid(Toroidal2DPhysics space, Ship ship){
		
		Set<Asteroid> asteroids = space.getAsteroids();
		double maxDistance = Double.MIN_VALUE;
		
		Asteroid furthestAsteroid = null;
		
		for(Asteroid asteroid : asteroids){
			//if a ship is not already assigned to that asteroid
			if(!myKnowledge.asteroidToShipContains(asteroid)){
				
				double dist = space.findShortestDistance(ship.getPosition(), asteroid.getPosition());
				
				//ensure asteroid is minable and closest
				if(asteroid.isMineable() && dist > maxDistance && isPathObstructedByMineable(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50, space)){
					maxDistance = dist;
					furthestAsteroid = asteroid;
				}
			}
		}
		return furthestAsteroid;	
	}
	
	private Asteroid pickFurthestObstructedByBeaconAsteroid(Toroidal2DPhysics space, Ship ship){
		
		Set<Asteroid> asteroids = space.getAsteroids();
		double maxDistance = Double.MIN_VALUE;
		
		Asteroid furthestAsteroid = null;
		
		for(Asteroid asteroid : asteroids){
			//if a ship is not already assigned to that asteroid
			if(!myKnowledge.asteroidToShipContains(asteroid)){
				
				double dist = space.findShortestDistance(ship.getPosition(), asteroid.getPosition());
				
				//ensure asteroid is minable and closest
				if(asteroid.isMineable() && dist > maxDistance && isPathObstructedByBeacon(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50, space)){
					maxDistance = dist;
					furthestAsteroid = asteroid;
				}
			}
		}
		return furthestAsteroid;	
	}
	
	
	
	
	/**
	 * McGovern's pickHighestValueFreeAsteroid method, picks the asteroid with the most total resources
	 */
	private Asteroid pickHighestAsteroid(Toroidal2DPhysics space, Ship ship) {

		//sets up asteroid search
		int highestValue = Integer.MIN_VALUE;
		Asteroid highestAsteroid = null;
		
		//picks the highest value mineable asteroid in our knowledge representation
		for(Asteroid asteroid : myKnowledge.getAsteroids()){
			if (!myKnowledge.asteroidToShipContains(asteroid)) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() > highestValue) {
					highestValue = asteroid.getResources().getTotal();
					highestAsteroid = asteroid;
				}
			}
		}
		return highestAsteroid;
	}
	
	private Asteroid pickHighestUnobstructedAsteroid(Toroidal2DPhysics space, Ship ship) {

		//sets up asteroid search
		int highestValue = Integer.MIN_VALUE;
		Asteroid highestAsteroid = null;
		
		//picks the highest value mineable asteroid in our knowledge representation
		for(Asteroid asteroid : myKnowledge.getAsteroids()){
			if (!myKnowledge.asteroidToShipContains(asteroid)) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() > highestValue && space.isPathClearOfObstructions(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50)) {
					highestValue = asteroid.getResources().getTotal();
					highestAsteroid = asteroid;
				}
			}
		}
		return highestAsteroid;
	}
	
	private Asteroid pickHighestObstructedByMineableAsteroid(Toroidal2DPhysics space, Ship ship) {

		//sets up asteroid search
		int highestValue = Integer.MIN_VALUE;
		Asteroid highestAsteroid = null;
		
		//picks the highest value mineable asteroid in our knowledge representation
		for(Asteroid asteroid : myKnowledge.getAsteroids()){
			if (!myKnowledge.asteroidToShipContains(asteroid)) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() > highestValue && isPathObstructedByMineable(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50, space)) {
					highestValue = asteroid.getResources().getTotal();
					highestAsteroid = asteroid;
				}
			}
		}
		return highestAsteroid;
	}
	
	private Asteroid pickHighestObstructedByBeaconAsteroid(Toroidal2DPhysics space, Ship ship) {

		//sets up asteroid search
		int highestValue = Integer.MIN_VALUE;
		Asteroid highestAsteroid = null;
		
		//picks the highest value mineable asteroid in our knowledge representation
		for(Asteroid asteroid : myKnowledge.getAsteroids()){
			if (!myKnowledge.asteroidToShipContains(asteroid)) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() > highestValue && isPathObstructedByBeacon(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50, space)) {
					highestValue = asteroid.getResources().getTotal();
					highestAsteroid = asteroid;
				}
			}
		}
		return highestAsteroid;
	}

	
	
	
	private Asteroid pickLowestAsteroid(Toroidal2DPhysics space, Ship ship) {

		//sets up asteroid search
		int lowestValue = Integer.MAX_VALUE;
		Asteroid lowestAsteroid = null;
		
		//picks the highest value mineable asteroid in our knowledge representation
		for(Asteroid asteroid : myKnowledge.getAsteroids()){
			if (!myKnowledge.asteroidToShipContains(asteroid)) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() < lowestValue) {
					lowestValue = asteroid.getResources().getTotal();
					lowestAsteroid = asteroid;
				}
			}
		}
		return lowestAsteroid;
	}
	
	private Asteroid pickLowestUnobstructedAsteroid(Toroidal2DPhysics space, Ship ship) {

		//sets up asteroid search
		int lowestValue = Integer.MAX_VALUE;
		Asteroid lowestAsteroid = null;
		
		//picks the highest value mineable asteroid in our knowledge representation
		for(Asteroid asteroid : myKnowledge.getAsteroids()){
			if (!myKnowledge.asteroidToShipContains(asteroid)) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() < lowestValue && space.isPathClearOfObstructions(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50)) {
					lowestValue = asteroid.getResources().getTotal();
					lowestAsteroid = asteroid;
				}
			}
		}
		return lowestAsteroid;
	}

	private Asteroid pickLowestObstructedByMineableAsteroid(Toroidal2DPhysics space, Ship ship) {

		//sets up asteroid search
		int lowestValue = Integer.MAX_VALUE;
		Asteroid lowestAsteroid = null;
		
		//picks the highest value mineable asteroid in our knowledge representation
		for(Asteroid asteroid : myKnowledge.getAsteroids()){
			if (!myKnowledge.asteroidToShipContains(asteroid)) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() < lowestValue && isPathObstructedByMineable(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50, space)) {
					lowestValue = asteroid.getResources().getTotal();
					lowestAsteroid = asteroid;
				}
			}
		}
		return lowestAsteroid;
	}
	
	private Asteroid pickLowestObstructedByBeaconAsteroid(Toroidal2DPhysics space, Ship ship) {

		//sets up asteroid search
		int lowestValue = Integer.MAX_VALUE;
		Asteroid lowestAsteroid = null;
		
		//picks the highest value mineable asteroid in our knowledge representation
		for(Asteroid asteroid : myKnowledge.getAsteroids()){
			if (!myKnowledge.asteroidToShipContains(asteroid)) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() < lowestValue && isPathObstructedByBeacon(ship.getPosition(), asteroid.getPosition(), space.getAllObjects(), 50, space)) {
					lowestValue = asteroid.getResources().getTotal();
					lowestAsteroid = asteroid;
				}
			}
		}
		return lowestAsteroid;
	}

	/**
	 * Override method for finishing turn.
	 */
	@Override
	public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {

		//this is just one ship for now
		//adds an asteroid and records the gain in resources, if ship gained resources
		for(Object ship : myKnowledge.getTeamShips()){
			startTurnResources = ((Ship) ship).getResources().getTotal();

			//if number of resources owned by ship has increased (has collected asteroid)
			if(startTurnResources > lastResources ){
				//add to number of resources collected by agent
				resourcesCollected += startTurnResources - lastResources;
				lastResources = startTurnResources;
				
			//resources have been returned. Reset them
			} else if(startTurnResources < lastResources){
				
				startTurnResources = 0;
				lastResources = 0;
				
				timeEnd = space.getCurrentTimestep();
				int elapsedTime = timeEnd - timeStart;
				
				//fitness function: resources collected divided by the amount of time elapsed
				population.setCurrentMemberFitness((double)resourcesCollected/(double)elapsedTime);
				
				resourcesCollected = 0;
				
				//Go to next member, and is genreation is finished make a new one
				currentPolicy = population.getNextMember();

				if (population.isGenerationFinished()) {
					
					population.makeNextGeneration();
					
					currentPolicy = population.getCurrentMember();
				}
				
				//save the current state of the population every time a new member is updated			
				XStream xstream = new XStream();
				xstream.alias("GAPopulation", GAPopulation.class);
				
				try { 
					// if you want to compress the file, change FileOuputStream to a GZIPOutputStream
					//save class information to knowledge file
					xstream.toXML(population, new FileOutputStream(new File(getKnowledgeFile())));
					System.out.println("population written");
				} catch (XStreamException e) {
					// if you get an error, handle it somehow as it means your knowledge didn't save
					// the error will happen the first time you run
					System.out.println("population write failed");
				} catch (FileNotFoundException e) {

				}
			}
			
		}
	}
	
	/**
	 * Override method for getting graphics. Does nothing for now.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Set<SpacewarGraphics> getGraphics() {
		HashSet<SpacewarGraphics> graphics = new HashSet<SpacewarGraphics>();
		if (graphByShip != null) {
			for (Graph graph : graphByShip.values()) {
				// uncomment to see the full graph
				//graphics.addAll(graph.getAllGraphics());
				graphics.addAll(graph.getSolutionPathGraphics());
			}
		}
		HashSet<SpacewarGraphics> newGraphicsClone = (HashSet<SpacewarGraphics>) graphics.clone();
		graphics.clear();
		return newGraphicsClone;
		//return null;
	}

	/**
	 * Override method to get powerups. Does nothing yet.
	 */
	@Override
	public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		return null;
	}
	
	/**
	 * Returns the GAState which is representative of the current environment.
	 * This state corresponds with the position of the gene which determines
	 * which asteroid a genetic algorithm ship will pursue
	 */
	private Asteroid decideAsteroidFromPolicy(Toroidal2DPhysics space, Ship ship) {
		
		/**
		 * Thresholds:
		 * Each gene in the chromosome corresponds to which decision to make
		 * THe first gene tells the ship to pursue the closest asteroid
		 * The second gene tells the ship to pursue the furthest asteroid
		 * The thrid gene tells the ship to pursue the highest value asteroid
		 * The fourth gene tells the ship to pursue the lowest value asteroid
		 * The rest of genes did not work how we wanted, and are not currently implemetned
		 */
		boolean tarNearest = currentPolicy.getGeneValueAtPos(0);
		boolean tarFurthest = currentPolicy.getGeneValueAtPos(1);
		boolean tarHighest = currentPolicy.getGeneValueAtPos(2);
		boolean tarLowest = currentPolicy.getGeneValueAtPos(3);
		//Did not work, so this bit is unused for now
		boolean tarRandom = false;
		//Did not work, so this bit is unused for now
		boolean tarUnobstructed = false;
		//Did not work, so this bit is unused for now
		boolean tarObstructedByMineable = false;
		//Did not work, so this bit is unused for now
		boolean tarObstructedByBeacon = false;
				
		//Pick nearest if multiple values were enabled		
		if(tarNearest && (tarFurthest || tarHighest || tarLowest))
		{
			tarFurthest = false;
			tarHighest = false;
			tarLowest = false;
		}
		//Otherwise pick highest if nearest was not enabled but multiple values were
		else if(tarHighest && (tarFurthest || tarLowest))
		{
			tarFurthest = false;
			tarLowest = false;
		}
		//Otherwise pick furthest if lowest and furthest were enabled
		else if(tarFurthest && tarLowest)
			tarLowest = false;
		
		//IF both unobstraucted and an obstructed gene were enabled, pick unobstructed
		if(tarUnobstructed && (tarObstructedByMineable || tarObstructedByBeacon))
		{
			tarObstructedByMineable = false;
			tarObstructedByBeacon = false;
		}
		
		//Pick obstructed by beacon if both obstructed genes were enabled
		else if(tarObstructedByMineable && tarObstructedByBeacon)
			tarObstructedByBeacon = false;
		
		//Chose result based on enabled gene
		//NOTE: Many of these will not ever occur since we disabled all of the Obstructed genes.
		if(tarNearest && !(tarUnobstructed || tarObstructedByMineable || tarObstructedByBeacon))
			return pickNearestAsteroid(space, ship);
		if(tarNearest && tarUnobstructed)
			return pickNearestUnobstructedAsteroid(space, ship);
		if(tarNearest && tarObstructedByMineable)
			return pickNearestObstructedByMineableAsteroid(space, ship);
		if(tarNearest && tarObstructedByBeacon)
			return pickNearestObstructedByBeaconAsteroid(space, ship);
		
		if(tarFurthest && !(tarUnobstructed || tarObstructedByMineable || tarObstructedByBeacon))
			return pickFurthestAsteroid(space, ship);
		if(tarFurthest && tarUnobstructed)
			return pickFurthestUnobstructedAsteroid(space, ship);
		if(tarFurthest && tarObstructedByMineable)
			return pickFurthestObstructedByMineableAsteroid(space, ship);
		if(tarFurthest && tarObstructedByBeacon)
			return pickFurthestObstructedByBeaconAsteroid(space, ship);
		
		if(tarHighest && !(tarUnobstructed || tarObstructedByMineable || tarObstructedByBeacon))
			return pickHighestAsteroid(space, ship);
		if(tarHighest && tarUnobstructed)
			return pickHighestUnobstructedAsteroid(space, ship);
		if(tarHighest && tarObstructedByMineable)
			return pickHighestObstructedByMineableAsteroid(space, ship);
		if(tarHighest && tarObstructedByBeacon)
			return pickHighestObstructedByBeaconAsteroid(space, ship);
		
		if(tarLowest && !(tarUnobstructed || tarObstructedByMineable || tarObstructedByBeacon))
			return pickLowestAsteroid(space, ship);
		if(tarLowest && tarUnobstructed)
			return pickLowestUnobstructedAsteroid(space, ship);
		if(tarLowest && tarObstructedByMineable)
			return pickLowestObstructedByMineableAsteroid(space, ship);
		if(tarLowest && tarObstructedByBeacon)
			return pickLowestObstructedByBeaconAsteroid(space, ship);
			
		
		//If nothing was enabled, pick the closest asteroid
		return pickNearestAsteroid(space, ship);
	}
	
	/**
	 * Dr. McGovern's A Star Method
	 */
	private AbstractAction getAStarPathToGoal(Toroidal2DPhysics space, Ship ship, Position goalPosition) {
		AbstractAction newAction;
		
		Graph graph = AStarSearch.createGraphToGoalWithBeacons(space, ship, goalPosition, new Random());
		Vertex[] path = graph.findAStarPath(space);
		followPathAction = new FollowPathAction(path);
		//followPathAction.followNewPath(path);
		newAction = followPathAction.followPath(space, ship);
		graphByShip.put(ship.getId(), graph);
		return newAction;
	}
	
	//Check if there is a mineable ateroid in the path between us and the target
	public boolean isPathObstructedByMineable(Position startPosition, Position goalPosition, Set<AbstractObject> obstructions, int freeRadius, Toroidal2DPhysics space) {
		Vector2D pathToGoal = space.findShortestDistanceVector(startPosition,  goalPosition); 	// Shortest straight line path from startPosition to goalPosition
		double distanceToGoal = pathToGoal.getMagnitude();										// Distance of straight line path

		boolean pathIsObstructed = false; // Boolean showing whether or not the path is clear
		
		// Calculate distance between obstruction center and path (including buffer for ship movement)
		// Uses hypotenuse * sin(theta) = opposite (on a right hand triangle)
		Vector2D pathToObstruction; // Vector from start position to obstruction
		double angleBetween; 		// Angle between vector from start position to obstruction
		
		// Loop through obstructions
		for (Asteroid asteroid: myKnowledge.getMineableAsteroids()) {
			// If the distance to the obstruction is greater than the distance to the end goal, ignore the obstruction
			pathToObstruction = space.findShortestDistanceVector(startPosition, asteroid.getPosition());
		    if (pathToObstruction.getMagnitude() > distanceToGoal) {
				continue;
			}
		    
			// Ignore angles > 90 degrees
			angleBetween = Math.abs(pathToObstruction.angleBetween(pathToGoal));
			if (angleBetween > Math.PI/2) {
				continue;
			}

			// Compare distance between obstruction and path with buffer distance
			if ((pathToObstruction.getMagnitude() * Math.sin(angleBetween) < asteroid.getRadius() + freeRadius*1.5)) {
				pathIsObstructed = true;
				break;
			}
		}
		
		return pathIsObstructed;
		
	}
	
	//Check if there is a beacon in the path between us and the target
	public boolean isPathObstructedByBeacon(Position startPosition, Position goalPosition, Set<AbstractObject> obstructions, int freeRadius, Toroidal2DPhysics space) {
		Vector2D pathToGoal = space.findShortestDistanceVector(startPosition,  goalPosition); 	// Shortest straight line path from startPosition to goalPosition
		double distanceToGoal = pathToGoal.getMagnitude();										// Distance of straight line path

		boolean pathIsObstructed = false; // Boolean showing whether or not the path is clear
		
		// Calculate distance between obstruction center and path (including buffer for ship movement)
		// Uses hypotenuse * sin(theta) = opposite (on a right hand triangle)
		Vector2D pathToObstruction; // Vector from start position to obstruction
		double angleBetween; 		// Angle between vector from start position to obstruction
		
		// Loop through obstructions
		for (Beacon beacon: myKnowledge.getBeacons()) {
			// If the distance to the obstruction is greater than the distance to the end goal, ignore the obstruction
			pathToObstruction = space.findShortestDistanceVector(startPosition, beacon.getPosition());
		    if (pathToObstruction.getMagnitude() > distanceToGoal) {
				continue;
			}
		    
			// Ignore angles > 90 degrees
			angleBetween = Math.abs(pathToObstruction.angleBetween(pathToGoal));
			if (angleBetween > Math.PI/2) {
				continue;
			}

			// Compare distance between obstruction and path with buffer distance
			if ((pathToObstruction.getMagnitude() * Math.sin(angleBetween) < beacon.getRadius() + freeRadius*1.5)) {
				pathIsObstructed = true;
				break;
			}
		}
		
		return pathIsObstructed;
		
	}


}

