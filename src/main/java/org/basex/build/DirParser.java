package org.basex.build;

import static org.basex.core.Text.*;
import static org.basex.data.DataText.*;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.basex.core.Prop;
import org.basex.core.cmd.Store;
import org.basex.data.*;
import org.basex.io.IO;
import org.basex.io.IOContent;
import org.basex.io.IOFile;
import org.basex.io.input.*;
import org.basex.util.TokenBuilder;
import org.basex.util.Util;
import org.basex.util.list.StringList;

/**
 * This class recursively scans files and directories and parses all
 * relevant files.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class DirParser extends Parser {
  /** Number of skipped files to log. */
  private static final int SKIPLOG = 10;
  /** Skipped files. */
  private final StringList skipped = new StringList();
  /** File pattern. */
  private final Pattern filter;
  /** Initial file path. */
  private final String root;

  /** Parse archives in directories. */
  private final boolean archives;
  /** Skip corrupt files in directories. */
  private final boolean skipCorrupt;
  /** Add ignored files as raw files. */
  private final boolean addRaw;
  /** Raw parsing. */
  private final boolean rawParser;

  /** Database path for storing binary files. */
  protected IOFile rawPath;
  /** Last source. */
  private IO lastSrc;
  /** Parser reference. */
  private Parser parser;
  /** Element counter. */
  private int c;

  /**
   * Constructor.
   * @param source source path
   * @param pr database properties
   * @param path future database path
   */
  public DirParser(final IO source, final Prop pr, final IOFile path) {
    super(source, pr);
    final String parent = source.dir();
    root = parent.endsWith("/") ? parent : parent + '/';
    skipCorrupt = prop.is(Prop.SKIPCORRUPT);
    archives = prop.is(Prop.ADDARCHIVES);
    addRaw = prop.is(Prop.ADDRAW);
    rawParser = prop.get(Prop.PARSER).toLowerCase(Locale.ENGLISH).
        equals(DataText.M_RAW);

    filter = !source.isDir() && !source.isArchive() ? null :
      Pattern.compile(IOFile.regex(pr.get(Prop.CREATEFILTER)));
    // choose binary storage if (disk-based) database path is known and
    // if raw parser or "add raw" option were chosen
    rawPath = path != null && (addRaw || rawParser) ?
        new IOFile(path, M_RAW) : null;
  }

  @Override
  public void parse(final ParserListener build) throws IOException {
    build.meta.filesize = 0;
    build.meta.original = src.path();
    parse(build, src);
  }

  /**
   * Parses the specified file or its children.
   * @param b builder
   * @param io current input
   * @throws IOException I/O exception
   */
  private void parse(final ParserListener b, final IO io) throws IOException {
    if(io.isDir()) {
      // only {@link IOFile} instances can have children
      for(final IO f : ((IOFile) io).children()) parse(b, f);
    } else {
      src = io;

      // loop through all (potentially zipped) files
      while(io.more(archives)) {
        //[RS]: Check stop
        //b.checkStop();

        // add file size for database meta information
        final long l = io.length();
        if(l != -1) b.meta.filesize += l;

        // use global target as path prefix
        String targ = target;
        String path = io.path();

        // add relative path without root (prefix) and file name (suffix)
        final String name = io.name();
        if(path.endsWith('/' + name)) {
          path = path.substring(0, path.length() - name.length());
          if(path.startsWith(root)) path = path.substring(root.length());
          targ = (targ + path).replace("//", "/");
        }

        // check if file passes the name filter pattern
        boolean exclude = false;
        if(filter != null) {
          String nm = io.name();
          if(Prop.WIN) nm = name.toLowerCase(Locale.ENGLISH);
          exclude = !filter.matcher(nm).matches();
        }

        if(exclude) {
          // exclude file: check if will be added as raw file
          if(addRaw && rawPath != null) {
            Store.store(io.inputSource(), new IOFile(rawPath, targ + name));
          }
        } else {
          if(rawParser) {
            // store input in raw format if database path is known
            if(rawPath != null) {
              Store.store(io.inputSource(), new IOFile(rawPath, targ + name));
            }
          } else {
            // store input as XML
            boolean ok = true;
            IO in = io;
            if(skipCorrupt) {
              // parse file twice to ensure that it is well-formed
              try {
                // cache file contents to allow or speed up a second run
                if(!(io instanceof IOContent)) {
                  in = new IOContent(io.read());
                  in.name(io.name());
                }
                parser = Parser.singleParser(in, prop, targ);
                MemBuilder.build("", parser);
              } catch(final IOException ex) {
                Util.debug(ex.getMessage());
                skipped.add(io.path());
                ok = false;
              }
            }

            // parse file
            if(ok) {
              parser = Parser.singleParser(in, prop, targ);
              parser.parse(b);
            }
            parser = null;
            // dump debug data
            if(Prop.debug && (++c & 0x3FF) == 0) Util.err(";");
          }
        }
      }
    }
  }

  @Override
  public String info() {
    final TokenBuilder tb = new TokenBuilder();
    if(!skipped.isEmpty()) {
      tb.add(SKIPPED).add(COL).add(NL);
      final int s = skipped.size();
      for(int i = 0; i < s && i < SKIPLOG; i++) {
        tb.add(LI).add(skipped.get(i)).add(NL);
      }
      if(s > SKIPLOG) {
        tb.add(LI).addExt(MORE_SKIPPED_X, s - SKIPLOG).add(NL);
      }
    }
    return tb.toString();
  }

  @Override
  public String det() {
    return parser != null ? parser.detail() : src.path();
  }

  @Override
  public double prog() {
    if(parser != null) return parser.progress();
    if(lastSrc == src) return 1;
    lastSrc = src;
    return Math.random();
  }

  @Override
  public void close() throws IOException {
    if(parser != null) parser.close();
  }
}
