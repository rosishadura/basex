package org.basex.build;

import static org.basex.data.DataText.*;

import java.io.*;

import org.basex.core.*;
import org.basex.core.cmd.*;
import org.basex.data.*;
import org.basex.io.*;
import org.basex.io.in.DataInput;
import org.basex.io.input.*;
import org.basex.io.out.*;
import org.basex.io.out.DataOutput;
import org.basex.io.random.*;
import org.basex.util.*;

/**
 * This class creates a database instance on disk. The storage layout is
 * described in the {@link Data} class.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class DiskBuilder extends Builder {
  /** Database table. */
  private DataOutput tout;
  /** Database texts. */
  private DataOutput xout;
  /** Database values. */
  private DataOutput vout;
  /** Output stream for temporary values. */
  private DataOutput sout;

  /** Database context. */
  final Context context;

  /**
   * Constructor.
   * @param nm name of database
   * @param s source
   * @param ctx database context
   */
  public DiskBuilder(final String nm, final IO s, final Context ctx) {
    super(nm, s);
    context = ctx;
  }

  @Override
  public void init() throws IOException {
    final MetaData md = new MetaData(name, context);
    if(source == null) {
      md.original = "";
      md.filesize = 0;
      md.time = System.currentTimeMillis();
    } else {
      md.original = source.path();
      md.filesize = source.length();
      md.time = source.timeStamp();
    }
    md.dirty = true;

    // calculate optimized output buffer sizes to reduce disk fragmentation
    final Runtime rt = Runtime.getRuntime();
    int bs = (int) Math.min(md.filesize,
        Math.min(1 << 22, rt.maxMemory() - rt.freeMemory() >> 2));
    bs = Math.max(IO.BLOCKSIZE, bs - bs % IO.BLOCKSIZE);

    // drop old database (if available) and create new one
    DropDB.drop(name, context);
    context.mprop.dbpath(name).md();

    tout = new DataOutput(new TableOutput(md, DATATBL));
    xout = new DataOutput(md.dbfile(DATATXT), bs);
    vout = new DataOutput(md.dbfile(DATAATV), bs);
    sout = new DataOutput(md.dbfile(DATATMP), bs);

    listener = new DiskParserListener(md, tout, xout, vout, sout);

  }

  @Override
  public Data finish() throws IOException {
    close();

    // copy temporary values into database table
    final TableAccess ta = new TableDiskAccess(listener.meta, true);
    final DataInput in = new DataInput(listener.meta.dbfile(DATATMP));
    for(; spos < listener.ssize; ++spos)
      ta.write4(in.readNum(), 8, in.readNum());
    ta.close();
    in.close();
    listener.meta.dbfile(DATATMP).delete();

    // return database instance
    final DiskData data = new DiskData(listener.meta, listener.tags,
        listener.atts, listener.path, listener.ns);
    data.finishUpdate();
    return data;
  }

  @Override
  public void abort() {
    try {
      close();
    } catch(final IOException ex) {
      Util.debug(ex);
    }
    if(listener.meta != null) DropDB.drop(listener.meta.name, context);
  }

  @Override
  public void close() throws IOException {
    if(tout != null) tout.close();
    if(xout != null) xout.close();
    if(vout != null) vout.close();
    if(sout != null) sout.close();
    super.close();
    tout = null;
    xout = null;
    vout = null;
    sout = null;
  }
}
