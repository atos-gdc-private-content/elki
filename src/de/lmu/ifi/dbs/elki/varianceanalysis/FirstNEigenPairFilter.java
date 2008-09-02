package de.lmu.ifi.dbs.elki.varianceanalysis;

import de.lmu.ifi.dbs.elki.math.linearalgebra.EigenPair;
import de.lmu.ifi.dbs.elki.math.linearalgebra.SortedEigenPairs;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizable;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.ParameterException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterEqualConstraint;

import java.util.ArrayList;
import java.util.List;

/**
 * The FirstNEigenPairFilter marks the n highest eigenpairs as strong
 * eigenpairs, where n is a user specified number.
 * 
 * @author Elke Achtert
 */
// todo parameter comments
public class FirstNEigenPairFilter extends AbstractParameterizable implements EigenPairFilter {
  /**
   * OptionID for
   * {@link #N_PARAM}
   */
  public static final OptionID EIGENPAIR_FILTER_N = OptionID.getOrCreateOptionID("pca.filter.n",
      "The number of strong eigenvectors: n eigenvectors with the n highest"
      + "eigenvalues are marked as strong eigenvectors.");

  /**
   * Parameter n.
   */
  private final IntParameter N_PARAM = new IntParameter(EIGENPAIR_FILTER_N,
      new GreaterEqualConstraint(0));

  /**
   * The threshold for strong eigenvectors: n eigenvectors with the n highest
   * eigenvalues are marked as strong eigenvectors.
   */
  private double n;

  /**
   * Provides a new EigenPairFilter that sorts the eigenpairs in decending order
   * of their eigenvalues and marks the first n eigenpairs as strong eigenpairs.
   */
  public FirstNEigenPairFilter() {
    super();
    // this.debug = true;
    addOption(N_PARAM);
  }

  public FilteredEigenPairs filter(SortedEigenPairs eigenPairs) {
    StringBuffer msg = new StringBuffer();
    if(this.debug) {
      msg.append("\nsortedEigenPairs ").append(eigenPairs.toString());
      msg.append("\nn = ").append(n);
    }

    // init strong and weak eigenpairs
    List<EigenPair> strongEigenPairs = new ArrayList<EigenPair>();
    List<EigenPair> weakEigenPairs = new ArrayList<EigenPair>();

    // determine strong and weak eigenpairs
    for(int i = 0; i < eigenPairs.size(); i++) {
      EigenPair eigenPair = eigenPairs.getEigenPair(i);
      if(i < n) {
        strongEigenPairs.add(eigenPair);
      }
      else {
        weakEigenPairs.add(eigenPair);
      }
    }

    if(this.debug) {
      msg.append("\nstrong EigenPairs = ").append(strongEigenPairs);
      msg.append("\nweak EigenPairs = ").append(weakEigenPairs);
      debugFine(msg.toString());
    }

    return new FilteredEigenPairs(weakEigenPairs, strongEigenPairs);
  }

  public String parameterDescription() {
    StringBuffer description = new StringBuffer();
    description.append(FirstNEigenPairFilter.class.getName());
    description.append(" sorts the eigenpairs in decending order " + "of their eigenvalues and marks the first n eigenpairs " + "as strong eigenpairs.\n");
    description.append(optionHandler.usage("", false));
    return description.toString();
  }

  /**
   * Calls the super method and sets additionally the value of the parameter
   * {@link #N_PARAM}.
   */
  @Override
  public String[] setParameters(String[] args) throws ParameterException {
    String[] remainingParameters = super.setParameters(args);

    // n
    n = N_PARAM.getValue();

    return remainingParameters;
  }
}
