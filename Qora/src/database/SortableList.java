package database;

import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import utils.ObserverMessage;
import utils.Pair;

public class SortableList<T, U> extends AbstractList<Pair<T, U>> implements Observer {

	private DBMap<T, U> db;
	private int index;
	private boolean descending;
	private int position;
	private Iterator<T> iterator;
	private Pattern pattern;
	private int size;
	private Pair<T, U> lastValue;
	private Collection<T> keys;
	private List<String> additionalFilterFields;
	
	private static final Logger LOGGER = Logger.getLogger(SortableList.class);
	public SortableList(DBMap<T, U> db)
	{
		this.db = db;
		
		//LOAD DEFAULT ITERATOR
		this.index = DBMap.DEFAULT_INDEX;
		this.size = db.size();
		this.descending = false;
		this.iterator = this.filter(db.getIterator(DBMap.DEFAULT_INDEX, this.descending));
		this.position = 0;
		additionalFilterFields = new ArrayList<String>();
	}
	
	public SortableList(DBMap<T, U> db, Collection<T> keys)
	{
		this.db = db;
		this.keys = keys;
		
		//LOAD DEFAULT ITERATOR
		this.index = DBMap.DEFAULT_INDEX;
		this.size = keys.size();
		this.descending = false;
		this.iterator = keys.iterator();
		this.position = 0;
		additionalFilterFields = new ArrayList<String>();
	}
	
	public void registerObserver()
	{
		this.db.addObserver(this);
	}
	
	public void removeObserver()
	{
		this.db.deleteObserver(this);
	}
	
	
	@Override
	public Pair<T, U> get(int i) {
		
		//CHECK IF LAST VALUE
		if(this.position-1 == i && this.lastValue != null)
		{
			return this.lastValue;
		}
		
		if(i < this.position)
		{
			//RESET ITERATOR
			if(this.keys != null)
			{
				this.iterator = this.filter(this.keys.iterator());
			}
			else
			{
				this.iterator = this.filter(this.db.getIterator(this.index, this.descending));
			}
			
			this.position = 0;
		}
		
		//ITERATE UNTIL WE ARE AT THE POSITION
		while(this.position < i)
		{
			this.iterator.next();
			this.position++;
		}
		
		//RETURN
		T key = this.iterator.next();
		U value = this.db.get(key);
		this.position++;
		this.lastValue = new Pair<T, U>(key, value);
		return this.lastValue;
		
	}

	@Override
	public int size() {
		return this.size;
	}
	
	public void sort(int index)
	{
		this.sort(index, false);
	}
	
	public void sort(int index, boolean descending)
	{
		this.index = index;
		this.descending = descending;
		
		if(this.keys != null)
		{
			this.size = this.keys.size();
			this.iterator = this.keys.iterator();
		}
		else
		{
			this.size = db.size();
			this.iterator = this.filter(this.db.getIterator(index, descending));
		}
		
		this.position = 0;
		this.lastValue = null;
	}

	@Override
	public void update(Observable o, Object object) {
		
		ObserverMessage message = (ObserverMessage) object;
		if(message.getType() == this.db.getObservableData().get(DBMap.NOTIFY_ADD) || message.getType() == this.db.getObservableData().get(DBMap.NOTIFY_REMOVE))
		{
			//RESET DATA
			this.sort(this.index, this.descending);
		}
		
	}
	
	private Iterator<T> filter(Iterator<T> iterator)
	{
		if(this.pattern != null)
		{
			List<T> keys = new ArrayList<T>();
			
			Main:while(iterator.hasNext())
			{
				T key = iterator.next();
				String keyString = key.toString();
				
				Matcher matcher = this.pattern.matcher(keyString);
				if(matcher.find())
				{
					keys.add(key);
					continue Main;
				}
				
				U value = this.db.get(key);
				
				for (String fieldToSearch : additionalFilterFields) {
					
					try {
						Field field = value.getClass().getDeclaredField(fieldToSearch);
						field.setAccessible(true);
						String searchVal = (String) field.get(value);
						
						matcher = this.pattern.matcher(searchVal);
						if(matcher.find())
						{
							keys.add(key);
							continue Main;
						}
						
					} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
						
						LOGGER.error(e.getMessage(),e);
					}
				}
				
			}
			
			this.size = keys.size();
			return (Iterator<T>) keys.iterator();
		}
		
		return iterator;
	}

	public void setFilter(String filter) 
	{
		this.pattern = Pattern.compile(".*" + filter + ".*");
		this.sort(this.index, this.descending);
	}
	
	/**
	 * Add a field to the filter list
	 * @param fieldname this should be a field of type String in the Class of generic type U
	 */
	public void addFilterField(String fieldname)
	{
		if(!additionalFilterFields.contains(fieldname))
		{
			additionalFilterFields.add(fieldname);
		}
	}
	
}
