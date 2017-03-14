/**
 * gobandroid 
 * by Marcus -Ligi- Bueschleb 
 * http://ligi.de
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as 
 * published by the Free Software Foundation; 
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details. 
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 **/

package org.ligi.gobandroid_hd.logic.sgf;

import java.util.ArrayList;
import java.util.List;
import org.ligi.gobandroid_hd.logic.CellImpl;
import org.ligi.gobandroid_hd.logic.GoDefinitions;
import org.ligi.gobandroid_hd.logic.GoGame;
import org.ligi.gobandroid_hd.logic.GoGameMetadata;
import org.ligi.gobandroid_hd.logic.GoMove;
import org.ligi.gobandroid_hd.logic.markers.CircleMarker;
import org.ligi.gobandroid_hd.logic.markers.SquareMarker;
import org.ligi.gobandroid_hd.logic.markers.TextMarker;
import org.ligi.gobandroid_hd.logic.markers.TriangleMarker;
import org.ligi.tracedroid.logging.Log;
import static org.ligi.gobandroid_hd.logic.GoGame.MoveStatus.VALID;

/**
 * class for load games in SGF File-Format
 */
public class SGFReader {

    public final static int BREAKON_NOTHING = 0;
    public final static int BREAKON_FIRSTMOVE = 1;

    public final static int DEFAULT_SGF_TRANSFORM = 0;

    private String act_param = "";
    private String act_cmd = "";
    private String last_cmd = "";
    private GoGame game = null;
    private byte size = -1;
    private int predef_count_b = 0;
    private int predef_count_w = 0;
    private boolean break_pulled = false;

    private final GoGameMetadata metadata;
    private final List<GoMove> variationList;

    private final String sgf;
    private final ISGFLoadProgressCallback callback;
    private final int breakon;
    private final int transform;

    private SGFReader(String sgf, ISGFLoadProgressCallback callback, int breakon, int transform) {
        this.sgf = sgf;
        this.callback = callback;
        this.breakon = breakon;

        this.transform = transform;
        metadata = new GoGameMetadata();
        variationList = new ArrayList<>();

    }

    public static GoGame sgf2game(String sgf, ISGFLoadProgressCallback callback) {
        return sgf2game(sgf, callback, BREAKON_NOTHING, DEFAULT_SGF_TRANSFORM);
    }

    public static GoGame sgf2game(String sgf, ISGFLoadProgressCallback callback, int breakon) {
        return sgf2game(sgf, callback, breakon, DEFAULT_SGF_TRANSFORM);
    }

    /**
     * @param sgf
     * @param callback
     * @param breakon
     * @param transform - bit 1 => mirror y ; bit 2 => mirror x ; bit 3 => swap x/y
     * @return
     */
    public static GoGame sgf2game(String sgf, ISGFLoadProgressCallback callback, int breakon, int transform) {
        return new SGFReader(sgf, callback, breakon, transform).getGame();
    }

    public interface ISGFLoadProgressCallback {
        void progress(int act, int max, int progress_val);
    }

    private GoGame getGame() {
        try {
            byte opener = 0;
            boolean escape = false;
            boolean consuming_param = false;

            for (int p = 0; ((p < sgf.length()) && (!break_pulled)); p++) {
                char act_char = sgf.charAt(p);
                if (!consuming_param)
                    // consuming command
                    switch (act_char) {
                        case '\r':
                        case '\n':
                        case ';':
                        case '\t':
                        case ' ':
                            if (!act_cmd.isEmpty())
                                last_cmd = act_cmd;
                            act_cmd = "";
                            break;

                        case '[':
                            if (act_cmd.isEmpty())
                                act_cmd = last_cmd;

                            // for files without SZ - e.g. ggg-intermediate-11.sgf
                            if ((game == null) && (act_cmd.equals("AB") || act_cmd.equals("AW") || act_cmd.equals("TR") || act_cmd.equals("SQ") || act_cmd.equals("LB") || act_cmd.equals("MA"))) {
                                size = 19;
                                game = new GoGame((byte) 19);
                            }
                            consuming_param = true;
                            act_param = "";
                            break;

                        case '(':

                            if (!consuming_param) {
                                // for files without SZ
                                if ((opener == 1) && (game == null)) {
                                    size = 19;
                                    game = new GoGame((byte) 19);
                                    variationList.add(getOrCreateGame().getActMove());
                                }

                                opener++;

                                // push the move we where to the stack to return
                                // here
                                // after the variation

                                // if (param_level!=0) break;
                                Log.i("   !!! opening variation" + game);
                                if (game != null) {

                                    variationList.add(getOrCreateGame().getActMove());
                                }

                                last_cmd = "";
                                act_cmd = "";
                            }
                            break;
                        case ')':
                            if (variationList.size() > 0) {
                                GoMove lastMove = variationList.get(variationList.size() - 1);
                                getOrCreateGame().jump(lastMove);
                                variationList.remove(lastMove);
                                Log.w("popping variaton from stack");
                            } else {
                                Log.w("variation vector underrun!!");
                            }

                            last_cmd = "";
                            act_cmd = "";

                            break;

                        default:
                            act_cmd += sgf.charAt(p);

                    }
                else {

                    // consuming param
                    switch (act_char) {
                        case ']': // closing command parameter -> can process
                            // command
                            // now
                            if ((game != null) && (callback != null))
                                callback.progress(p, sgf.length(), getOrCreateGame().getActMove().getMovePos());
                            if (!escape) {
                                consuming_param = false;
                                processCommand();
                            }
                            break;
                        case '\\':
                            if (escape) {
                                act_param += (char) act_char;
                                escape = false;
                            } else
                                escape = true;
                            break;
                        default:
                            act_param += (char) act_char;
                            escape = false;
                            break;

                    }
                }

            }

            if (game != null) {
                if(game.getActMove().isFirstMove() && predef_count_w == 0 && predef_count_b > 0) {
                    game.getActMove().setIsBlackToMove(true); // probably handicap - so  make white
                    // to  move - very  important for cloud game and handicap
                }
                game.setMetadata(metadata);
            }

            //if (game.getFirstMove())
            return game;

        } catch (Exception e) { // some weird sgf - we want to catch to not FC
            // and have the chance to send the sgf to analysis
            e.printStackTrace();
            Log.w("Problem parsing SGF " + e);
        }

        return null;

    }

    private void processCommand() {
        int param_x = 0, param_y = 0;

        // if we have a minimum of 2 chars in param - could be coords - so parse
        if (act_param.length() >= 2) {
            param_x = act_param.charAt(((transform & 4) == 0) ? 0 : 1) - 'a';
            param_y = act_param.charAt(((transform & 4) == 0) ? 1 : 0) - 'a';

            if (size>-1) {
                if ((transform & 1) > 0)
                    param_y = (byte) (size - 1 - param_y);

                if ((transform & 2) > 0)
                    param_x = (byte) (size - 1 - param_x);
            }
        }

        final CellImpl cell = new CellImpl(param_x, param_y);
        // if command is empty -> use the last command
        if (act_cmd.isEmpty())
            act_cmd = last_cmd;

        // marker section - info here http://www.red-bean.com/sgf/properties.html
        switch (act_cmd) {
            case "LB":
                final String[] inner = act_param.split(":");
                final String txt = (inner.length > 1) ? inner[1] : "X";

                getOrCreateGame().getActMove().addMarker(new TextMarker(cell, txt));
                break;

            // mark with x
            case "Mark":
            case "MA":
                getOrCreateGame().getActMove().addMarker(new TextMarker(cell, "X"));
                break;

            case "SL":
                getOrCreateGame().getActMove().addMarker(new TextMarker(cell, "+"));
                break;

            // mark with triangle
            case "TR":
                getOrCreateGame().getActMove().addMarker(new TriangleMarker(cell));
                break;

            case "SQ": // mark with square
                getOrCreateGame().getActMove().addMarker(new SquareMarker(cell));
                break;

            case "CR": // mark with circle
                getOrCreateGame().getActMove().addMarker(new CircleMarker(cell));
                break;

            case "GN": // Game Name
                metadata.setName(act_param);
                break;

            case "DI": // Difficulty ( found in goproblems.com files )
                metadata.setDifficulty(act_param);
                break;

            case "PW": // Player White Name
                metadata.setWhiteName(act_param);
                break;

            case "PB": // Player Black Name
                metadata.setBlackName(act_param);
                break;

            case "WR": // Player White Rank
                metadata.setWhiteRank(act_param);
                break;

            case "BR": // Player Black Rank
                metadata.setBlackRank(act_param);
                break;

            case "RE": // Game Result
                metadata.setResult(act_param);
                break;

            case "DT":
                metadata.setDate(act_param);
                break;

            case "SO": // Source
                metadata.setSource(act_param);
                break;

            case "KM":
                try {
                    getOrCreateGame().setKomi(Float.parseFloat(act_param));
                } catch (NumberFormatException ignored) {
                    // catch a bad komi-statement like seen KM[] - would now even catch KM[crashme]
                }
                break;
        }

        // size command
        if (act_cmd.equals("SiZe") || act_cmd.equals("SZ")) {
            act_param = act_param.replaceAll("[^0-9]", "");
            if (!act_param.isEmpty()) { // got a SGF with SZ[]
                size = Byte.parseByte(act_param);
                if ((game == null) || (game.getBoardSize() != size)) {
                    game = new GoGame(size);
                    variationList.add(game.getActMove());
                }
            }
        }

        // comment command

        if (act_cmd.equals("Comment") || act_cmd.equals("C")) {
            if (game != null)
                game.getActMove().setComment(act_param);
        }

        // move command
        if (act_cmd.equals("Black") || act_cmd.equals("B") || act_cmd.equals("W") || act_cmd.equals("White")) {

            // if still no game open -> open one with default size
            if (game == null) {
                game = new GoGame((byte) 19);
                variationList.add(game.getActMove());
            }

            if ((breakon & BREAKON_FIRSTMOVE) > 0)
                break_pulled = true;

            if (act_param.length() == 0)
                game.pass();
            else {
                /* if (game.getActMove().isFirstMove()) {
                    game.getActMove().setIsBlackToMove();
                } */
                final GoGame.MoveStatus moveStatus = game.do_move(cell);
                if (moveStatus != VALID) {
                    Log.w("There was a problem in this game");
                }
            }


        }

        // TODO support AddEmpty
        // handle predefined stones ( mostly handicap stones ) in SGF
        if (act_cmd.equals("AddBlack") || act_cmd.equals("AB") || act_cmd.equals("AW") || act_cmd.equals("AddWhite")) {

            Log.i("Adding stone " + act_cmd + " " + act_param + " at " + cell);

            if (game == null) { // create a game if it is
                // not
                // there yet
                game = new GoGame((byte) 19);
                variationList.add(game.getActMove());
            }

            if (act_param.length() != 0) {
                if (game.isBlackToMove() && (act_cmd.equals("AB") || act_cmd.equals("AddBlack"))) {
                    predef_count_b++;
                    game.getHandicapBoard().setCell(cell, GoDefinitions.STONE_BLACK);
                }

                if (game.isBlackToMove() && (act_cmd.equals("AW") || act_cmd.equals("AddWhite"))) {
                    predef_count_w++;
                    game.getHandicapBoard().setCell(cell, GoDefinitions.STONE_WHITE);
                }
            } else
                Log.w("AB / AW command without param");

        }

        last_cmd = act_cmd;
        act_cmd = "";
        act_param = "";
    }

    private GoGame getOrCreateGame() {
        if (game == null) {
            size = 19;
            game = new GoGame(size);
        }
        return game;
    }
}
