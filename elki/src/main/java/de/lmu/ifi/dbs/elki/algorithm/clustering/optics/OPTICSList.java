package de.lmu.ifi.dbs.elki.algorithm.clustering.optics;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2014
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.Comparator;

import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDBIDDataStore;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDoubleDataStore;
import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDVar;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DoubleDBIDList;
import de.lmu.ifi.dbs.elki.database.ids.DoubleDBIDListIter;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.query.range.RangeQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.math.MathUtil;
import de.lmu.ifi.dbs.elki.utilities.Alias;
import de.lmu.ifi.dbs.elki.utilities.datastructures.QuickSelect;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;

/**
 * The OPTICS algorithm for density-based hierarchical clustering.
 * 
 * This version is implemented using a list and quickselect, instead of using a
 * heap.
 * 
 * Reference:
 * <p>
 * M. Ankerst, M. Breunig, H.-P. Kriegel, and J. Sander:<br />
 * OPTICS: Ordering Points to Identify the Clustering Structure. <br/>
 * In: Proc. ACM SIGMOD Int. Conf. on Management of Data (SIGMOD '99).
 * </p>
 * 
 * @author Elke Achtert
 * @author Erich Schubert
 * 
 * @apiviz.composedOf OPTICSList.Instance
 *
 * @param <O> the type of DatabaseObjects handled by the algorithm
 */
@Title("OPTICS: Density-Based Hierarchical Clustering")
@Description("Algorithm to find density-connected sets in a database based on the parameters 'minPts' and 'epsilon' (specifying a volume). These two parameters determine a density threshold for clustering.")
@Reference(authors = "M. Ankerst, M. Breunig, H.-P. Kriegel, and J. Sander", //
title = "OPTICS: Ordering Points to Identify the Clustering Structure", //
booktitle = "Proc. ACM SIGMOD Int. Conf. on Management of Data (SIGMOD '99)", //
url = "http://dx.doi.org/10.1145/304181.304187")
@Alias({ "OPTICS", "de.lmu.ifi.dbs.elki.algorithm.clustering.OPTICS" })
public class OPTICSList<O> extends AbstractOPTICS<O> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(OPTICSList.class);

  /**
   * Constructor.
   * 
   * @param distanceFunction Distance function
   * @param epsilon Epsilon value
   * @param minpts Minpts value
   */
  public OPTICSList(DistanceFunction<? super O> distanceFunction, double epsilon, int minpts) {
    super(distanceFunction, epsilon, minpts);
  }

  /**
   * Run OPTICS on the database.
   * 
   * @param db Database
   * @param relation Relation
   * @return Result
   */
  public ClusterOrderResult<DoubleDistanceClusterOrderEntry> run(Database db, Relation<O> relation) {
    return new Instance(db, relation).run();
  }

  /**
   * Instance for processing a single data set.
   *
   * @author Erich Schubert
   */
  private class Instance implements Comparator<DBIDRef> {
    /**
     * Holds a set of processed ids.
     */
    private ModifiableDBIDs processedIDs;

    /**
     * Current list of candidates.
     */
    private ArrayModifiableDBIDs candidates;

    /**
     * Predecessor storage.
     */
    WritableDBIDDataStore predecessor;

    /**
     * Reachability storage.
     */
    WritableDoubleDataStore reachability;

    /**
     * Output cluster order.
     */
    ClusterOrderResult<DoubleDistanceClusterOrderEntry> clusterOrder;

    /**
     * IDs to process.
     */
    private DBIDs ids;

    /**
     * Progress for logging.
     */
    FiniteProgress progress;

    /**
     * Range query.
     */
    RangeQuery<O> rangeQuery;

    /**
     * Constructor for a single data set.
     *
     * @param db Database
     * @param relation Data relation
     */
    public Instance(Database db, Relation<O> relation) {
      ids = relation.getDBIDs();
      processedIDs = DBIDUtil.newHashSet(ids.size());
      candidates = DBIDUtil.newArray();
      predecessor = DataStoreUtil.makeDBIDStorage(ids, DataStoreFactory.HINT_HOT);
      reachability = DataStoreUtil.makeDoubleStorage(ids, DataStoreFactory.HINT_DB | DataStoreFactory.HINT_HOT, Double.POSITIVE_INFINITY);
      clusterOrder = new ClusterOrderResult<>(db, ids, "OPTICS Clusterorder", "optics-clusterorder");
      progress = LOG.isVerbose() ? new FiniteProgress("OPTICS", ids.size(), LOG) : null;
      DistanceQuery<O> dq = db.getDistanceQuery(relation, getDistanceFunction());
      rangeQuery = db.getRangeQuery(dq, epsilon);
    }

    @Override
    public int compare(DBIDRef o1, DBIDRef o2) {
      return Double.compare(reachability.doubleValue(o2), reachability.doubleValue(o1));
    }

    /**
     * Process the data set.
     * 
     * @return Cluster order result.
     */
    public ClusterOrderResult<DoubleDistanceClusterOrderEntry> run() {
      for(DBIDIter iditer = ids.iter(); iditer.valid(); iditer.advance()) {
        if(processedIDs.contains(iditer)) {
          continue;
        }
        expandClusterOrder(iditer);
      }
      LOG.ensureCompleted(progress);
      return clusterOrder;
    }

    /**
     * OPTICS-function expandClusterOrder.
     * 
     * @param objectID the currently processed object
     */
    protected void expandClusterOrder(DBIDRef objectID) {
      candidates.add(objectID);
      predecessor.putDBID(objectID, objectID);
      reachability.put(objectID, Double.POSITIVE_INFINITY);

      DBIDVar cur = DBIDUtil.newVar(), prev = DBIDUtil.newVar();
      while(!candidates.isEmpty()) {
        int last = candidates.size() - 1;
        QuickSelect.quickSelect(candidates, this, last);
        candidates.assignVar(last, cur);
        candidates.remove(last);
        processedIDs.add(cur);
        { // Build cluster order entry
          DBID currentID = DBIDUtil.deref(cur);
          DBID prevID = DBIDUtil.deref(predecessor.assignVar(cur, prev));
          double dist = reachability.doubleValue(cur);
          clusterOrder.add(new DoubleDistanceClusterOrderEntry(currentID, prevID, dist));
          LOG.incrementProcessed(progress);
        }

        DoubleDBIDList neighbors = rangeQuery.getRangeForDBID(cur, epsilon);
        if(neighbors.size() >= minpts) {
          DoubleDBIDListIter neighbor = neighbors.iter();
          double coreDistance = neighbor.seek(minpts - 1).doubleValue();

          for(neighbor.seek(0); neighbor.valid(); neighbor.advance()) {
            if(processedIDs.contains(neighbor)) {
              continue;
            }
            double reach = MathUtil.max(neighbor.doubleValue(), coreDistance);
            double prevreach = reachability.doubleValue(neighbor);
            if(reach < prevreach) {
              reachability.put(neighbor, reach);
              predecessor.putDBID(neighbor, cur);
              if(prevreach >= Double.POSITIVE_INFINITY) {
                candidates.add(neighbor);
              }
            }
          }
        }
      }
    }
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer<O> extends AbstractOPTICS.Parameterizer<O> {
    @Override
    protected OPTICSList<O> makeInstance() {
      return new OPTICSList<>(distanceFunction, epsilon, minpts);
    }
  }
}