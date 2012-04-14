package org.basex.io.input;

import static org.basex.build.BuildText.*;
import static org.basex.util.Token.*;

import java.io.*;

import org.basex.build.*;
import org.basex.core.*;
import org.basex.data.*;
import org.basex.index.*;
import org.basex.index.path.*;
import org.basex.io.*;
import org.basex.util.*;
import org.basex.util.list.*;

/**
 * Parser event listener.
 * @author BaseX Team 2005-12, BSD License
 * @author Rositsa Shadura
 */
public abstract class ParserListener {
  /** Tree structure. */
  public final PathSummary path;
  /** Namespace index. */
  public final Namespaces ns;
  /** Meta data on built database. */
  public final MetaData meta;
  /** Tag name index. */
  public final Names tags;
  /** Attribute name index. */
  public final Names atts;

  /** Parent stack. */
  private final IntList pstack = new IntList();
  /** Tag stack. */
  private final IntList tstack = new IntList();
  /** Current tree height. */
  private int level;
  /** Element counter. */
  private int c;

  /** Number of cached size values. */
  public int ssize;

  /**
   * Constructor.
   * @param p path summary
   * @param n namespaces
   * @param t tags
   * @param a attributes
   * @param m meta data
   */
  public ParserListener(final PathSummary p, final Namespaces n, final Names t,
      final Names a, final MetaData m) {
    path = p;
    ns = n;
    tags = t;
    atts = a;
    meta = m;
  }

  /**
   * Opens a document node.
   * @param value document name
   * @throws IOException I/O exception
   */
  public final void startDoc(final byte[] value) throws IOException {
    path.index(0, Data.DOC, level);
    pstack.set(level++, meta.size);
    addDoc(value);
    ns.open();
  }

  /**
   * Closes a document node.
   * @throws IOException I/O exception
   */
  public final void endDoc() throws IOException {
    final int pre = pstack.get(--level);
    setSize(pre, meta.size - pre);
    meta.ndocs++;
    ns.close(meta.size);
  }

  /**
   * Adds a new namespace; called by the building instance.
   * @param pref the namespace prefix
   * @param uri namespace uri
   */
  public final void startNS(final byte[] pref, final byte[] uri) {
    ns.add(pref, uri, meta.size);
  }

  /**
   * Opens a new element node.
   *
   * @param nm tag name
   * @param att attributes
   * @throws IOException I/O exception
   */
  public final void startElem(final byte[] nm, final Atts att)
      throws IOException {
    addElem(nm, att);
    ++level;
  }

  /**
   * Stores an empty element.
   * @param nm tag name
   * @param att attributes
   * @throws IOException I/O exception
   */
  public final void emptyElem(final byte[] nm, final Atts att)
      throws IOException {
    addElem(nm, att);
    final int pre = pstack.get(level);
    ns.close(pre);
    if(att.size() > IO.MAXATTS) setSize(pre, meta.size - pre);
  }

  /**
   * Closes an element.
   * @throws IOException I/O exception
   */
  public final void endElem() throws IOException {
    // [RS] check stop
    --level;
    final int pre = pstack.get(level);
    setSize(pre, meta.size - pre);
    ns.close(pre);
  }

  /**
   * Stores a text node.
   * @param value text value
   * @throws IOException I/O exception
   */
  public final void text(final byte[] value) throws IOException {
    if(value.length != 0) addText(value, Data.TEXT);
  }

  /**
   * Stores a comment.
   * @param value comment text
   * @throws IOException I/O exception
   */
  public final void comment(final byte[] value) throws IOException {
    addText(value, Data.COMM);
  }

  /**
   * Stores a processing instruction.
   * @param pi processing instruction name and value
   * @throws IOException I/O exception
   */
  public final void pi(final byte[] pi) throws IOException {
    addText(pi, Data.PI);
  }

  /**
   * Sets the document encoding.
   * @param enc encoding
   */
  public final void encoding(final String enc) {
    meta.encoding = eq(enc, UTF8, UTF82) ? UTF8 : enc;
  }

  /**
   * Adds a document node to the database.
   * @param value name of the document
   * @throws IOException I/O exception
   */
  protected abstract void addDoc(byte[] value) throws IOException;

  /**
   * Adds an element node to the database. This method stores a preliminary size
   * value; if this node has further descendants, {@link #setSize} must be
   * called to set the final size value.
   * @param dist distance to parent
   * @param nm the tag name reference
   * @param asize number of attributes
   * @param uri namespace uri reference
   * @param ne namespace flag
   * @throws IOException I/O exception
   */
  protected abstract void addElem(int dist, int nm, int asize, int uri,
      boolean ne) throws IOException;

  /**
   * Adds an attribute to the database.
   * @param nm attribute name
   * @param value attribute value
   * @param dist distance to parent
   * @param uri namespace uri reference
   * @throws IOException I/O exception
   */
  protected abstract void addAttr(int nm, byte[] value, int dist, int uri)
      throws IOException;

  /**
   * Adds a text node to the database.
   * @param value the token to be added (tag name or content)
   * @param dist distance to parent
   * @param kind the node kind
   * @throws IOException I/O exception
   */
  protected abstract void addText(byte[] value, int dist, byte kind)
      throws IOException;

  /**
   * Stores a size value to the specified table position.
   * @param pre pre reference
   * @param size value to be stored
   * @throws IOException I/O exception
   */
  public abstract void setSize(int pre, int size) throws IOException;

  // PRIVATE METHODS ==========================================================

  /**
   * Adds an element node to the storage.
   *
   * @param nm tag name
   * @param att attributes
   * @throws IOException I/O exception
   */
  private void addElem(final byte[] nm, final Atts att) throws IOException {
    // get tag reference
    int n = tags.index(nm, null, true);

    path.index(n, Data.ELEM, level);

    // cache pre value
    final int pre = meta.size;
    // remember tag id and parent reference
    tstack.set(level, n);
    pstack.set(level, pre);

    // get and store element references
    final int dis = level != 0 ? pre - pstack.get(level - 1) : 1;
    final int as = att.size();
    final boolean ne = ns.open();
    int u = ns.uri(nm, true);
    addElem(dis, n, Math.min(IO.MAXATTS, as + 1), u, ne);

    // get and store attribute references
    for(int a = 0; a < as; ++a) {
      n = atts.index(att.name(a), att.string(a), true);
      u = ns.uri(att.name(a), false);
      path.index(n, Data.ATTR, level + 1, att.string(a), meta);
      addAttr(n, att.string(a), Math.min(IO.MAXATTS, a + 1), u);
    }

    // set leaf node information in index
    if(level > 1) tags.stat(tstack.get(level - 1)).setLeaf(false);

    if(Prop.debug && (c++ & 0x7FFFF) == 0) Util.err(".");

    // check if data ranges exceed database limits,
    // based on the storage details in {@link Data}
    limit(tags.size(), 0x8000, LIMITTAGS);
    limit(atts.size(), 0x8000, LIMITATTS);
    limit(ns.size(), 0x100, LIMITNS);
    if(meta.size < 0) limit(0, 0, LIMITRANGE);
  }

  /**
   * Checks a value limit and optionally throws an exception.
   * @param value value
   * @param limit limit
   * @param msg message
   * @throws IOException I/O exception
   */
  private void limit(final int value, final int limit, final String msg)
      throws IOException {
    if(value >= limit) // error(msg, parser.detail(), limit);
    error(msg, "", limit);
  }

  /**
   * Adds a simple text, comment or processing instruction to the database.
   * @param value the value to be added
   * @param kind the node type
   * @throws IOException I/O exception
   */
  private void addText(final byte[] value, final byte kind) throws IOException {
    final int l = level;
    if(l > 1) {
      final int tag = tstack.get(l - 1);
      // text node processing for statistics
      if(kind == Data.TEXT) tags.index(tag, value);
      // set leaf node information in index
      else tags.stat(tag).setLeaf(false);
    }

    path.index(0, kind, l, value, meta);
    addText(value, l == 0 ? 1 : meta.size - pstack.get(l - 1), kind);
  }

  /**
   * Throws an error message.
   * @param msg message
   * @param ext message extension
   * @throws IOException I/O exception
   */
  private static void error(final String msg, final Object... ext)
      throws IOException {
    throw new BuildException(msg, ext);
  }
}
