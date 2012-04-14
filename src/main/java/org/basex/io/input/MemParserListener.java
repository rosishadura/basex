package org.basex.io.input;

import org.basex.data.*;

/**
 * Parser listener for in-memory databases.
 * @author BaseX Team 2005-12, BSD License
 * @author Rositsa Shadura
 */
public class MemParserListener extends ParserListener {
  /** Data instance, where new data will be written. */
  private final MemData data;

  /**
   * Constructor.
   * @param d data instance, where new data will be written
   */
  public MemParserListener(final MemData d) {
    super(d.paths, d.nspaces, d.tagindex, d.atnindex, d.meta);
    data = d;
  }

  @Override
  protected void addDoc(final byte[] value) {
    data.doc(meta.size, 0, value);
    data.insert(meta.size);
  }

  @Override
  protected void addElem(final int dist, final int nm, final int asize,
      final int uri, final boolean ne) {
    data.elem(dist, nm, asize, asize, uri, ne);
    data.insert(meta.size);
  }

  @Override
  protected void addAttr(final int nm, final byte[] value, final int dist,
      final int uri) {
    data.attr(meta.size, dist, nm, value, uri, false);
    data.insert(meta.size);
  }

  @Override
  protected void addText(final byte[] value, final int dist, final byte kind) {
    data.text(meta.size, dist, value, kind);
    data.insert(meta.size);
  }

  @Override
  public void setSize(final int pre, final int size) {
    data.size(pre, Data.ELEM, size);
  }

}
