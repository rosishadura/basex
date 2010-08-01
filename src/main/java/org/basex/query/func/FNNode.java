package org.basex.query.func;

import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.QueryInfo;
import org.basex.query.QueryTokens;
import org.basex.query.expr.Expr;
import org.basex.query.item.Bln;
import org.basex.query.item.Item;
import org.basex.query.item.Nod;
import org.basex.query.item.QNm;
import org.basex.query.item.Str;
import org.basex.query.item.Type;
import org.basex.query.item.Uri;
import org.basex.util.TokenBuilder;

/**
 * Node functions.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-10, ISC License
 * @author Christian Gruen
 */
final class FNNode extends Fun {
  /**
   * Constructor.
   * @param i query info
   * @param f function definition
   * @param e arguments
   */
  protected FNNode(final QueryInfo i, final FunDef f, final Expr... e) {
    super(i, f, e);
  }

  @Override
  public Item atomic(final QueryContext ctx) throws QueryException {
    // functions have 0 or 1 arguments...
    final Item it = (expr.length != 0 ? expr[0] : checkCtx(ctx)).atomic(ctx);
    final boolean empty = it == null;

    switch(func) {
      case NODENAME:
        if(empty) return null;
        QNm qname = checkNode(it).qname();
        return qname != null && qname.atom().length != 0 ? qname : null;
      case DOCURI:
        if(empty) return null;
        final byte[] uri = checkNode(it).base();
        return uri.length == 0 ? null : Uri.uri(uri);
      case NILLED: // [CG] XQuery: nilled flag
        if(empty) return null;
        checkNode(it);
        return it.type != Type.ELM ? null : Bln.FALSE;
      case BASEURI:
        if(empty) return null;
        Nod n = checkNode(it);
        if(n.type != Type.ELM && n.type != Type.DOC && n.parent() == null)
          return null;
        Uri base = Uri.EMPTY;
        while(!base.absolute()) {
          if(n == null) {
            base = ctx.baseURI.resolve(base);
            break;
          }
          base = Uri.uri(n.base()).resolve(base);
          n = n.parent();
        }
        return base;
      case NAME:
        if(empty) return Str.ZERO;
        qname = checkNode(it).qname();
        return qname != null ? Str.get(qname.atom()) : Str.ZERO;
      case LOCNAME:
        if(empty) return Str.ZERO;
        qname = checkNode(it).qname();
        return qname != null ? Str.get(qname.ln()) : Str.ZERO;
      case NSURI:
        if(empty || it.type == Type.PI) return Uri.EMPTY;
        Nod node = checkNode(it);
        while(node != null) {
          qname = node.qname();
          if(qname == null) break;
          if(qname.uri != Uri.EMPTY) return qname.uri;
          node = node.parent();
        }
        return Uri.uri(ctx.nsElem);
      case ROOT:
        if(empty) return null;
        n = checkNode(it);
        while(n.parent() != null) n = n.parent();
        return n;
      case GENID:
        return empty ? Str.ZERO : Str.get(
            new TokenBuilder(QueryTokens.ID).add(checkNode(it).id()).finish());
      default:
        return super.atomic(ctx);
    }
  }

  @Override
  public boolean uses(final Use u, final QueryContext ctx) {
    return u == Use.CTX && expr.length == 0 || super.uses(u, ctx);
  }
}
