package org.basex.build;

import static org.basex.util.Token.*;
import java.io.IOException;

import org.basex.core.*;
import org.basex.io.IO;
import org.basex.io.input.*;

/**
 * This class defines an abstract parser for single resources.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public abstract class SingleParser extends Parser {
  /** Builder reference. */
  protected ParserListener builder;

  /**
   * Constructor.
   * @param source input source
   * @param pr database properties
   */
  protected SingleParser(final IO source, final Prop pr) {
    super(source, pr);
  }

  @Override
  public final void parse(final ParserListener build) throws IOException {
    builder = build;
    builder.startDoc(token(target + src.name()));
    parse();
    builder.endDoc();
  }

  /**
   * Parses the current input.
   * @throws IOException I/O exception
   */
  public abstract void parse() throws IOException;

  /**
   * Sets the database builder.
   * @param b builder instance
   * @return self reference
   */
  public SingleParser builder(final ParserListener b) {
    builder = b;
    return this;
  }
}
