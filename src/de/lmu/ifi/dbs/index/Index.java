package de.lmu.ifi.dbs.index;

import de.lmu.ifi.dbs.data.DatabaseObject;
import de.lmu.ifi.dbs.persistent.LRUCache;
import de.lmu.ifi.dbs.persistent.MemoryPageFile;
import de.lmu.ifi.dbs.persistent.PageFile;
import de.lmu.ifi.dbs.persistent.PersistentPageFile;
import de.lmu.ifi.dbs.utilities.optionhandling.*;
import de.lmu.ifi.dbs.database.Database;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract super class for all index classes.
 *
 * @author Elke Achtert (<a href="mailto:achtert@dbs.ifi.lmu.de">achtert@dbs.ifi.lmu.de</a>)
 */
public abstract class Index<O extends DatabaseObject, N extends Node<N,E>, E extends Entry> extends AbstractParameterizable {

  /**
   * Option string for parameter fileName.
   */
  public static final String FILE_NAME_P = "filename";

  /**
   * Description for parameter filename.
   */
  public static final String FILE_NAME_D = "a file name specifying the name of the file storing the index. "
                                           + "If this parameter is not set the index is hold in the main memory.";

  /**
   * The default pagesize.
   */
  public static final int DEFAULT_PAGE_SIZE = 4000;

  /**
   * Option string for parameter pagesize.
   */
  public static final String PAGE_SIZE_P = "pagesize";

  /**
   * Description for parameter filename.
   */
  public static final String PAGE_SIZE_D = "a positive integer value specifying the size of a page in bytes "
                                           + "(default is " + DEFAULT_PAGE_SIZE + " Byte)";

  /**
   * The default cachesize.
   */
  public static final int DEFAULT_CACHE_SIZE = Integer.MAX_VALUE;

  /**
   * Option string for parameter cachesize.
   */
  public static final String CACHE_SIZE_P = "cachesize";

  /**
   * Description for parameter cachesize.
   */
  public static final String CACHE_SIZE_D = "a positive integer value specifying the size of the cache in bytes "
                                            + "(default is Integer.MAX_VALUE)";

  /**
   * The name of the file for storing the index.
   */
  private String fileName;

  /**
   * The size of a page in bytes.
   */
  protected int pageSize;

  /**
   * The size of the cache in bytes.
   */
  protected int cacheSize;

  /**
   * The file storing the entries of this index.
   */
  protected PageFile<N> file;

  /**
   * True if this index is already initialized.
   */
  protected boolean initialized = false;

  /**
   * The capacity of a directory node (= 1 + maximum number of entries in a directory node).
   */
  protected int dirCapacity;

  /**
   * The capacity of a leaf node (= 1 + maximum number of entries in a leaf node).
   */
  protected int leafCapacity;

  /**
   * The minimum number of entries in a directory node.
   */
  protected int dirMinimum;

  /**
   * The minimum number of entries in a leaf node.
   */
  protected int leafMinimum;

  /**
   * The entry representing the root node.
   */
  private E rootEntry = createRootEntry();

  /**
   * Sets parameters file, pageSize and cacheSize.
   */
  public Index() {
    super();
    optionHandler.put(FILE_NAME_P, new Parameter(FILE_NAME_P, FILE_NAME_D, Parameter.Types.FILE));
    optionHandler.put(PAGE_SIZE_P, new Parameter(PAGE_SIZE_P, PAGE_SIZE_D, Parameter.Types.INT));
    optionHandler.put(CACHE_SIZE_P, new Parameter(CACHE_SIZE_P, CACHE_SIZE_D, Parameter.Types.INT));
  }

  /**
   * @see de.lmu.ifi.dbs.utilities.optionhandling.Parameterizable#setParameters(String[])
   */
  public String[] setParameters(String[] args) throws ParameterException {
    String[] remainingParameters = optionHandler.grabOptions(args);

    // filename
    if (optionHandler.isSet(FILE_NAME_P)) {
      fileName = optionHandler.getOptionValue(FILE_NAME_P);
    }
    else {
      fileName = null;
    }

    // pagesize
    if (optionHandler.isSet(PAGE_SIZE_P)) {
      try {
        pageSize = Integer.parseInt(optionHandler.getOptionValue(PAGE_SIZE_P));
        if (pageSize <= 0)
          throw new WrongParameterValueException(PAGE_SIZE_P, optionHandler.getOptionValue(PAGE_SIZE_P), PAGE_SIZE_D);
      }
      catch (NumberFormatException e) {
        throw new WrongParameterValueException(PAGE_SIZE_P, optionHandler.getOptionValue(PAGE_SIZE_P), PAGE_SIZE_D, e);
      }
    }
    else {
      pageSize = DEFAULT_PAGE_SIZE;
    }

    // cachesize
    if (optionHandler.isSet(CACHE_SIZE_P)) {
      try {
        cacheSize = Integer.parseInt(optionHandler.getOptionValue(CACHE_SIZE_P));
        if (cacheSize < 0)
          throw new WrongParameterValueException(CACHE_SIZE_P, optionHandler.getOptionValue(CACHE_SIZE_P), CACHE_SIZE_D);
      }
      catch (NumberFormatException e) {
        throw new WrongParameterValueException(CACHE_SIZE_P, optionHandler.getOptionValue(CACHE_SIZE_P), CACHE_SIZE_D, e);
      }
    }
    else {
      cacheSize = DEFAULT_CACHE_SIZE;
    }

    setParameters(args, remainingParameters);
    return remainingParameters;
  }

  /**
   * @see de.lmu.ifi.dbs.utilities.optionhandling.Parameterizable#getAttributeSettings()
   */
  public List<AttributeSettings> getAttributeSettings() {
    List<AttributeSettings> settings = new ArrayList<AttributeSettings>();

    AttributeSettings mySettings = new AttributeSettings(this);
    mySettings.addSetting(FILE_NAME_P, fileName);
    mySettings.addSetting(PAGE_SIZE_P, Integer.toString(pageSize));
    mySettings.addSetting(CACHE_SIZE_P, Integer.toString(cacheSize));

    return settings;
  }

  /**
   * Returns a description of the class and the required parameters.
   * <p/>
   * This description should be suitable for a usage description.
   *
   * @return String a description of the class and the required parameters
   */
  public String description() {
    return optionHandler.usage("", false);
  }

  /**
   * Returns the physical read access of this index.
   */
  public final long getPhysicalReadAccess() {
    return file.getPhysicalReadAccess();
  }

  /**
   * Returns the physical write access of this index.
   */
  public final long getPhysicalWriteAccess() {
    return file.getPhysicalWriteAccess();
  }

  /**
   * Returns the logical page access of this index.
   */
  public final long getLogicalPageAccess() {
    return file.getLogicalPageAccess();
  }

  /**
   * Resets the counters for page access.
   */
  public final void resetPageAccess() {
    file.resetPageAccess();
  }

  /**
   * Closes this index and the underlying file.
   * If this index has a oersistent file, all entries are written to disk.
   */
  public final void close() {
    file.close();
  }

  /**
   * Returns the entry representing the root if this index.
   *
   * @return the entry representing the root if this index
   */
  public final E getRootEntry() {
    return rootEntry;
  }

  /**
   * Returns the node with the specified id.
   *
   * @param nodeID the page id of the node to be returned
   * @return the node with the specified id
   */
  public final N getNode(int nodeID) {
    if (nodeID == rootEntry.getID())
      return getRoot();
    else {
      return file.readPage(nodeID);
    }
  }

  /**
   * Returns the node that is represented by the specified entry.
   *
   * @param entry the entry representing the node to be returned
   * @return the node that is represented by the specified entry
   */
  public final N getNode(E entry) {
    return getNode(entry.getID());
  }

  /**
   * Creates a header for this index structure.
   * Subclasses may need to overwrite this method.
   */
  protected IndexHeader createHeader() {
    return new IndexHeader(pageSize, dirCapacity, leafCapacity, dirMinimum, leafMinimum);
  }

  /**
   * Initializes this index from an existing persistent file.
   */
  protected void initializeFromFile() {
    if (fileName == null)
      throw new IllegalArgumentException("Parameter file name is not specified.");

    // init the file
    IndexHeader header = createHeader();
    this.file = new PersistentPageFile<N>(header, cacheSize, new LRUCache<N>(), fileName);

    this.dirCapacity = header.getDirCapacity();
    this.leafCapacity = header.getLeafCapacity();
    this.dirMinimum = header.getDirMinimum();
    this.leafMinimum = header.getLeafMinimum();

    if (this.debug) {
      StringBuffer msg = new StringBuffer();
      msg.append(getClass());
      msg.append("\n file = ").append(file.getClass());
      debugFine(msg.toString());
    }

    this.initialized = true;
  }

  /**
   * Initializes the index.
   *
   * @param object an object that will be stored in the index
   */
  protected final void initialize(O object) {
    // determine minimum and maximum entries in a node
    initializeCapacities(object, true);

    // init the file
    if (fileName == null) {
      this.file = new MemoryPageFile<N>(pageSize,
                                        cacheSize,
                                        new LRUCache<N>());
    }
    else {
      this.file = new PersistentPageFile<N>(createHeader(),
                                            cacheSize,
                                            new LRUCache<N>(),
                                            fileName);
    }

    // create empty root
    createEmptyRoot(object);

    if (this.debug) {
      StringBuffer msg = new StringBuffer();
      msg.append(getClass()).append("\n");
      msg.append(" file    = ").append(file.getClass()).append("\n");
      msg.append(" maximum number of dir entries = ").append((dirCapacity - 1)).append("\n");
      msg.append(" minimum number of dir entries = ").append(dirMinimum).append("\n");
      msg.append(" maximum number of leaf entries = ").append((leafCapacity - 1)).append("\n");
      msg.append(" minimum number of leaf entries = ").append(leafMinimum).append("\n");
      msg.append(" root    = ").append(getRoot());
      debugFine(msg.toString());
    }

    initialized = true;
  }

  /**
   * Returns the path to the root of this tree.
   *
   * @return the path to the root of this tree
   */
  protected final IndexPath<E> getRootPath() {
    return new IndexPath<E>(new IndexPathComponent<E>(rootEntry, null));
  }

  /**
   * Reads the root node of this index from the file.
   *
   * @return the root node of this index
   */
  protected N getRoot() {
    return file.readPage(rootEntry.getID());
  }

  /**
   * Inserts the specified object into this index.
   *
   * @param object the vector to be inserted
   */
  abstract public void insert(O object);

  /**
   * Inserts the specified objects into this index. If a bulk load mode
   * is implemented, the objects are inserted in one bulk.
   *
   * @param objects the objects to be inserted
   */
  abstract public void insert(List<O> objects);

  /**
   * Deletes the specified obect from this index.
   *
   * @param object the object to be deleted
   * @return true if this index did contain the object, false otherwise
   */
  abstract public boolean delete(O object);

  /**
   * Determines the maximum and minimum number of entries in a node.
   *
   * @param object an object that will be stored in the index
   * @param verbose flag to allow verbose messages
   */
  abstract protected void initializeCapacities(O object, boolean verbose);

  /**
   * Creates an empty root node and writes it to file.
   *
   * @param object an object that will be stored in the index
   */
  abstract protected void createEmptyRoot(O object);

  /**
   * Creates an entry representing the root node.
   *
   * @return an entry representing the root node
   */
  abstract protected E createRootEntry();

  /**
   * Creates a new leaf node with the specified capacity.
   *
   * @param capacity the capacity of the new node
   * @return a new leaf node
   */
  abstract protected N createNewLeafNode(int capacity);

  /**
   * Creates a new directory node with the specified capacity.
   *
   * @param capacity the capacity of the new node
   * @return a new directory node
   */
  abstract protected N createNewDirectoryNode(int capacity);

  /**
   * Performs necessary operations before inserting the specified entry.
   *
   * @param entry the entry to be inserted
   */
  abstract protected void preInsert(E entry);

  /**
   * Performs necessary operations after deleting the specified object.
   *
   * @param o the object to be deleted
   */
  abstract protected void postDelete(O o);

  /**
   * Sets the databse in the distance function of this index (if existing)
   * @param database the database
   */
  abstract public void setDatabase(Database<O> database);
}
