package org.basex.build;

import static org.basex.core.Text.*;
import static org.basex.util.Token.*;

import java.io.*;

import org.basex.core.*;
import org.basex.data.*;
import org.basex.io.*;
import org.basex.io.input.*;
import org.basex.util.*;

/**
 * This class provides an interface for building database instances. The
 * specified {@link Parser} sends events to this class whenever nodes are to be
 * added or closed. The builder implementation decides whether the nodes are
 * stored on disk or kept in memory.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public abstract class Builder extends Progress {
  /** Parser listener. */
  protected ParserListener listener;
  /** Source. */
  protected IO source;
  /** Parser instance. */
  private Parser parser;
  /** Database name. */
  protected final String name;

  /** Currently stored size value. */
  protected int spos;

  /**
   * Constructor.
   * @param nm name of database
   * @param s source input; used to copy some meta-data in the database.
   */
  protected Builder(final String nm, final IO s) {
    name = nm;
    source = s;
  }

  // PROGRESS INFORMATION =====================================================

  @Override
  protected final String tit() {
    return CREATING_DB;
  }

  @Override
  public final String det() {
    return spos == 0 ? parser.detail() : FINISHING_D;
  }

  @Override
  public final double prog() {
    return spos == 0 ? parser.progress() : (double) spos / listener.ssize;
  }

  // ABSTRACT METHODS =========================================================

  /**
   * Builds the database.
   * @param p parser
   * @return data database instance
   * @throws IOException I/O exception
   */
  public Data build(final Parser p) throws IOException {
    parser = p;
    init();

    final Performance perf = Prop.debug ? new Performance() : null;
    Util.debug(tit() + DOTS);

    // add document node and parse document
    parser.parse(listener);

    // no nodes inserted: add default document node
    if(listener.meta.size == 0) {
      listener.startDoc(token(name));
      listener.endDoc();
      listener.setSize(0, listener.meta.size);
    }
    // lastid should reflect the fact that the default document was added
    listener.meta.lastid = listener.meta.size - 1;

    Util.memory(perf);

    return finish();
  }

  /**
   * Initialized build process.
   * @throws IOException I/O exception
   */
  public abstract void init() throws IOException;

  /**
   * Finished build process.
   * @return data
   * @throws IOException I/O exception
   */
  public abstract Data finish() throws IOException;

  /**
   * Closes open references.
   * @throws IOException I/O exception
   */
  public void close() throws IOException {
    if(parser != null) {
      parser.close();
      parser = null;
    }
  }

}
