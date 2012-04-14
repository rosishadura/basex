package org.basex.io.input;

import java.io.*;

import org.basex.data.*;
import org.basex.index.*;
import org.basex.index.path.*;
import org.basex.io.*;
import org.basex.io.out.DataOutput;
import org.basex.util.*;

/**
 * Parser listener for disk-based databases.
 * @author BaseX Team 2005-12, BSD License
 * @author Rositsa Shadura
 */
public class DiskParserListener extends ParserListener {

  /** Attribute values. */
  private final DataOutput vout;
  /** Texts. */
  private final DataOutput xout;
  /** Table. */
  private final DataOutput tout;
  /** Sizes. */
  private final DataOutput sout;
  /** Text compression. */
  private final Compress comp;

  /**
   * Constructor.
   * @param md meta data
   * @param t table
   * @param x texts
   * @param v attribute values
   * @param s sizes
   */
  public DiskParserListener(final MetaData md, final DataOutput t,
      final DataOutput x, final DataOutput v, final DataOutput s) {
    super(new PathSummary(null), new Namespaces(), new Names(md), new Names(md), md);
    tout = t;
    xout = x;
    vout = v;
    sout = s;
    comp = new Compress();
  }

  @Override
  protected void addDoc(final byte[] value) throws IOException {
    tout.write1(Data.DOC);
    tout.write2(0);
    tout.write5(textOff(value, true));
    tout.write4(0);
    tout.write4(meta.size++);
  }

  @Override
  protected void addElem(final int dist, final int nm, final int asize,
      final int uri, final boolean ne) throws IOException {

    tout.write1(asize << 3 | Data.ELEM);
    tout.write2((ne ? 1 << 15 : 0) | nm);
    tout.write1(uri);
    tout.write4(dist);
    tout.write4(asize);
    tout.write4(meta.size++);
  }

  @Override
  protected void addAttr(final int nm, final byte[] value, final int dist,
      final int uri) throws IOException {

    tout.write1(dist << 3 | Data.ATTR);
    tout.write2(nm);
    tout.write5(textOff(value, false));
    tout.write4(uri);
    tout.write4(meta.size++);
  }

  @Override
  protected void addText(final byte[] value, final int dist, final byte kind)
      throws IOException {

    tout.write1(kind);
    tout.write2(0);
    tout.write5(textOff(value, true));
    tout.write4(dist);
    tout.write4(meta.size++);
  }

  @Override
  public void setSize(final int pre, final int size) throws IOException {
    sout.writeNum(pre);
    sout.writeNum(size);
    ++ssize;
  }

  /**
   * Calculates the text offset and writes the text value.
   * @param value value to be inlined
   * @param text text/attribute flag
   * @return inline value or text position
   * @throws IOException I/O exception
   */
  private long textOff(final byte[] value, final boolean text)
      throws IOException {

    // inline integer values...
    final long v = Token.toSimpleInt(value);
    if(v != Integer.MIN_VALUE) return v | IO.OFFNUM;

    // store text
    final DataOutput store = text ? xout : vout;
    final long off = store.size();
    final byte[] val = comp.pack(value);
    store.writeToken(val);
    return val == value ? off : off | IO.OFFCOMP;
  }

}
