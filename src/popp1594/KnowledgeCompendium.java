package popp1594;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;

/**
 *Knowledge Representation Class that stores & distributes information about all objects in the game.
 */
public class KnowledgeCompendium {
	//Declare class variables
	private HashMap <UUID, Ship> asteroidToShipMap;
	private HashMap <UUID, Boolean> aimingForBase;
	private ArrayList<Base> teamBases;
	private ArrayList<Ship> teamShips;
	private ArrayList<Asteroid> asteroids;
	private ArrayList<Beacon> beacons;
	private int numAsteroids;

	//Initializes values of the class
	public KnowledgeCompendium(){
		asteroidToShipMap = new HashMap<UUID, Ship>();
		aimingForBase = new HashMap<UUID, Boolean>();
		teamBases = new ArrayList<Base>();
		teamShips = new ArrayList<Ship>();
		asteroids = new ArrayList<Asteroid>();
		beacons = new ArrayList<Beacon>();
		numAsteroids = 0;
	}
	
	/**
	 * Add a ship
	 */
	public void addTeamShip(AbstractObject ship){
		teamShips.add((Ship) ship);
	}
	
	/**
	 * Add a base
	 */
	public void addTeamBase(AbstractObject base){
		teamBases.add((Base) base);
	}
	
	/**
	 * Add an asteroid
	 */
	public void addAsteroid(Asteroid asteroid){
		asteroids.add(asteroid);
	}
	
	/**
	 * Add a beacon
	 */
	public void addBeacon(AbstractObject beacon){
		beacons.add((Beacon) beacon);
	}
	
	/**
	 * Return list of bases
	 */
	public ArrayList<Base> getTeamBases(){
		return teamBases;
	}
	
	/**
	 * Return List of ships
	 */
	public ArrayList<Ship> getTeamShips(){
		return teamShips;
	}
	
	/**
	 * Return list of asteroids
	 */
	public ArrayList<Asteroid> getAsteroids(){
		return asteroids;
	}
	
	/**
	 * Return a list of minable asteroids
	 * @return
	 */
	public ArrayList<Asteroid> getMineableAsteroids() {
		ArrayList<Asteroid> mineAbleAsteroids = new ArrayList<Asteroid>();
		for(Asteroid asteroid : getAsteroids())
		{
			if(asteroid.isMineable())
				mineAbleAsteroids.add(asteroid);
		}
		return mineAbleAsteroids;
	}
	
	
	/**
	 * Return list of beacons
	 */
	public ArrayList<Beacon> getBeacons(){
		return beacons;
	}
	
	/**
	 * Remove all bases from list
	 */
	public void clearBases(){
		teamBases = new ArrayList<Base>();
	}
	
	/**
	 * Remove all ships from list
	 */
	public void clearShips(){
		teamShips = new ArrayList<Ship>();
	}
	
	/**
	 * Remove all asteroids from list
	 */
	public void clearAstroids(){
		asteroids = new ArrayList<Asteroid>();
	}
	
	/**
	 * Remove all beacons from list
	 */
	public void clearBeacons(){
		beacons = new ArrayList<Beacon>();
	}
	
	/**
	 * maps an asteroid to a ship, signifying that the ship is pursuing the asteroid
	 */
	public void mapAsteroidToShip(Asteroid asteroid, Ship ship){
		
		//we should check if this ship is not already pursuing this asteroid. If it isn't, then we'll go ahead and do nothing.
		//if it is, we'll go ahead and increment the number of asteroids pursued and add one? Perhaps we'll just print it. 
		if(asteroidToShipMap.containsKey(asteroid.getId())){
			asteroidToShipMap.put(asteroid.getId(), ship);
			return;
		} else {
			numAsteroids++;
			System.out.println(numAsteroids);
			asteroidToShipMap.put(asteroid.getId(), ship);
		}
		
	}
	
	/**
	 * maps a ship as returning to base
	 */
	public void mapShipAimingForBase(Ship ship, boolean bool){
		aimingForBase.put(ship.getId(), bool);
	}
	
	/**
	 * checks if an asteroid is being chased by a team ship
	 */
	public boolean asteroidToShipContains(Asteroid asteroid){
		return asteroidToShipMap.containsKey(asteroid);
	}
	
	/**
	 * checks if a given ship is included in the map that holds whether a given ship is returning to base
	 */
	public boolean aimingForBaseContains(Ship ship){
		return aimingForBase.containsKey(ship.getId());
	}
	
	/**
	 * checks whether the given ship is returning to base
	 */
	public boolean aimingForBase(Ship ship){
		return aimingForBase.get(ship.getId());
	}
	
}
