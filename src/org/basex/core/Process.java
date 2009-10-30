package org.basex.core;

import static org.basex.core.Text.*;
import java.io.IOException;
import java.io.OutputStream;

import org.basex.core.Commands.CmdPerm;
import org.basex.data.Data;
import org.basex.data.Nodes;
import org.basex.data.Result;
import org.basex.io.PrintOutput;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;
import org.basex.util.Performance;
import org.basex.util.TokenBuilder;

/**
 * This class provides the architecture for all internal command
 * implementations. It evaluates queries that are sent by the GUI, the client or
 * the standalone version.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-09, ISC License
 * @author Christian Gruen
 */
public abstract class Process extends Progress {
  /** Commands flag: standard. */
  protected static final int STANDARD = 256;
  /** Commands flag: data reference needed. */
  protected static final int DATAREF = 1024;

  /** Command arguments. */
  protected String[] args;
  /** Database context. */
  protected Context context;
  /** Database properties. */
  protected Prop prop;

  /** Container for query information. */
  protected TokenBuilder info = new TokenBuilder();
  /** Performance measurements. */
  protected Performance perf;
  /** Temporary query result. */
  protected Result result;

  /** Flags for controlling process evaluation. */
  private final int flags;

  /**
   * Constructor.
   * @param f command flags
   * @param a arguments
   */
  public Process(final int f, final String... a) {
    flags = f;
    args = a;
  }

  /**
   * Executes the process and serializes the results.
   * If an exception occurs, a {@link BaseXException} is thrown.
   * @param ctx database context
   * @param out output stream reference
   */
  public void exec(final Context ctx, final OutputStream out) {
    if(!execute(ctx, out instanceof PrintOutput ? (PrintOutput) out :
      new PrintOutput(out))) throw new BaseXException(info());
  }

  /**
   * Executes a process. This method should only be used if a command
   * does not return textual results.
   * @param ctx database context
   * @return success flag
   */
  public final boolean execute(final Context ctx) {
    return execute(ctx, null);
  }

  /**
   * Executes the process, prints the result and returns a success flag.
   * @param ctx database context
   * @param out output stream
   * @return success flag
   */
  public final boolean execute(final Context ctx, final PrintOutput out) {
    perf = new Performance();
    context = ctx;
    prop = ctx.prop;

    // check if process needs data reference
    final Data data = context.data();
    if(data() && data == null) return error(PROCNODB);

    // check permissions
    final User user = context.user;
    int up = user.perm;
    if(data != null) {
      final User us = data.meta.users.get(user.name);
      if(us != null) up = up & ~(User.READ | User.WRITE) | us.perm;
    }
    int fp = flags & (User.READ | User.WRITE | User.CREATE | User.ADMIN);
    if(updating(ctx)) fp |= User.WRITE;
    int i = 4;
    while(--i >= 0) {
      final int f = 1 << i;
      if((f & fp) != 0 && (f & up) == 0) break;
    }
    if(i != -1) return error(PERMNO, CmdPerm.values()[i]);

    try {
      return exec(out);
    } catch(final Throwable ex) {
      // catch unexpected errors...
      ex.printStackTrace();
      if(ex instanceof OutOfMemoryError) {
        Performance.gc(2);
        return error(PROCOUTMEM);
      }
      return error(PROCERR, this, ex.toString());
    }
  }

  /**
   * Returns the query information as a string.
   * @return info string
   */
  public final String info() {
    return info.toString();
  }

  /**
   * Returns the result set, generated by the last query.
   * @return result set
   */
  public final Result result() {
    return result;
  }

  /**
   * Returns if the command needs a data reference for processing.
   * @return result of check
   */
  public boolean data() {
    return (flags & DATAREF) != 0;
  }

  /**
   * Returns if the command performs updates.
   * @param ctx context reference
   * @return result of check
   */
  @SuppressWarnings("unused")
  public boolean updating(final Context ctx) {
    return false;
  }

  // PROTECTED METHODS ========================================================

  /**
   * Executes a process and serializes the result.
   * @param out output stream
   * @return success of operation
   * @throws IOException I/O exception
   */
  protected abstract boolean exec(final PrintOutput out) throws IOException;

  /**
   * Adds the error message to the message buffer {@link #info}.
   * @param msg error message
   * @param ext error extension
   * @return false
   */
  protected final boolean error(final String msg, final Object... ext) {
    info.reset();
    info.add(msg == null ? "" : msg, ext);
    return false;
  }

  /**
   * Adds information on the process execution.
   * @param str information to be added
   * @param ext extended info
   * @return true
   */
  protected final boolean info(final String str, final Object... ext) {
    if(prop.is(Prop.INFO)) {
      info.add(str, ext);
      info.add(Prop.NL);
    }
    return true;
  }

  /**
   * Performs the specified XQuery.
   * @param q query to be performed
   * @param err this string is thrown as exception if the results are no
   *    element nodes
   * @return result set
   */
  protected final Nodes query(final String q, final String err) {
    try {
      final String query = q == null ? "" : q;
      final QueryProcessor qu = new QueryProcessor(query, context);
      progress(qu);
      final Nodes nodes = qu.queryNodes();
      // check if all result nodes are tags
      if(err != null) {
        final Data data = context.data();
        for(int i = nodes.size() - 1; i >= 0; i--) {
          if(data.kind(nodes.nodes[i]) != Data.ELEM) {
            error(err);
            return null;
          }
        }
      }
      return nodes;
    } catch(final QueryException ex) {
      Main.debug(ex);
      error(ex.getMessage());
      return null;
    }
  }

  /**
   * Returns the command option.
   * @param typ options enumeration
   * @param <E> token type
   * @return option
   */
  public <E extends Enum<E>> E getOption(final Class<E> typ) {
    try {
      return Enum.valueOf(typ, args[0].toUpperCase());
    } catch(final Exception ex) {
      error(CMDWHICH, args[0]);
      return null;
    }
  }

  /**
   * Returns the specified string in quotes, if spaces are found.
   * @param s string to be quoted
   * @return quoted string
   */
  protected String quote(final String s) {
    final StringBuilder sb = new StringBuilder();
    if(!s.isEmpty()) {
      sb.append(' ');
      final boolean spc = s.indexOf(' ') != -1;
      if(spc) sb.append('"');
      sb.append(s);
      if(spc) sb.append('"');
    }
    return sb.toString();
  }

  /**
   * Returns the list of arguments.
   * @return arguments
   */
  protected final String args() {
    final StringBuilder sb = new StringBuilder();
    for(final String a : args) if(a != null) sb.append(quote(a));
    return sb.toString();
  }

  /**
   * Returns a string representation of the object. In the client/server
   * architecture, the command string is sent to and reparsed by the server.
   * @return string representation
   */
  @Override
  public String toString() {
    return Main.name(this).toUpperCase() + args();
  }
}
