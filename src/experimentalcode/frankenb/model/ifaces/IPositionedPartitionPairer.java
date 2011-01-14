/**
 * 
 */
package experimentalcode.frankenb.model.ifaces;

import java.util.List;

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.utilities.exceptions.UnableToComplyException;

/**
 * No description given.
 * 
 * @author Florian Frankenberger
 */
public interface IPositionedPartitionPairer {

  /**
   * When called requires you to return a list of PartitionPairings that should be 
   * calculated on the cluster. The partitions get automatically stored at the
   * right directories. The package quantity is just a hint for this algorithm and
   * can be ignored if not needed. If less than packageQuantity PartitionPairings are
   * returned, only as many as there are PartitionPairings packages get generated.
   * 
   * @param dataBase
   * @param partitions
   * @param packageQuantity
   * @return 
   * @return
   */
  public void makePairings(Database<NumberVector<?, ?>> dataBase, List<IPositionedPartition> partitions, IPartitionPairingStorage partitionPairingStorage, int packageQuantity) throws UnableToComplyException;
  
}
