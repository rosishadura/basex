package org.basex.build;

import java.io.*;

import org.basex.core.*;
import org.basex.data.*;
import org.basex.index.path.*;
import org.basex.io.*;
import org.basex.io.input.*;

/**
 * This class creates a database instance in main memory. The storage layout is
 * described in the {@link Data} class.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class MemBuilder extends Builder {
  /** Data reference. */
  private MemData data;
  /** Properties of the new database. */
  private Prop prop;

  /**
   * Constructor.
   * @param nm name of database
   * @param s source
   * @param p properties of the new database
   */
  public MemBuilder(final String nm, final IO s, final Prop p) {
    super(nm, s);
    prop = p;
  }

  /**
   * Builds the main memory database instance without database name.
   * @param parser parser
   * @return data database instance
   * @throws IOException I/O exception
   */
  public static MemData build1(final Parser parser) throws IOException {
    return build(parser.src.name(), parser);
  }

  /**
   * Builds a main memory database instance.
   * @param name name of database
   * @param parser parser
   * @return data database instance
   * @throws IOException I/O exception
   */
  public static MemData build(final String name, final Parser parser)
      throws IOException {
    return (MemData) new MemBuilder(name, parser.src, parser.prop).build(parser);
  }

  @Override
  public void init() {
    data = new MemData(null, null, new PathSummary(null), new Namespaces(), prop);

    final MetaData md = data.meta;
    md.name = name;
    // all contents will be indexed in main memory mode
    md.createtext = true;
    md.createattr = true;
    md.textindex = true;
    md.attrindex = true;
    if(source == null) {
      md.original = "";
      md.filesize = 0;
      md.time = System.currentTimeMillis();
    } else {
      md.original = source.path();
      md.filesize = source.length();
      md.time = source.timeStamp();
    }
    listener = new MemParserListener(data);
  }

  @Override
  public Data finish() {
    listener.path.finish(data);
    return data;
  }

}
