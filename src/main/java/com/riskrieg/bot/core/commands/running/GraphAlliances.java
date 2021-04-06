package com.riskrieg.bot.core.commands.running;

import com.mxgraph.layout.*;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.riskrieg.api.Riskrieg;
import com.riskrieg.bot.constant.BotConstants;
import com.riskrieg.bot.core.Command;
import com.riskrieg.bot.core.input.MessageInput;
import com.riskrieg.bot.core.input.SlashInput;
import com.riskrieg.bot.util.ImageUtil;
import com.riskrieg.constant.Colors;
import com.riskrieg.bot.util.Error;
import com.riskrieg.gamemode.Game;
import com.riskrieg.gamemode.IAlliances;
import com.riskrieg.nation.AllianceNation;
import com.riskrieg.nation.Nation;
import org.jgrapht.Graph;
import org.jgrapht.ext.*;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.AttachmentOption;

public class GraphAlliances extends Command {

  public GraphAlliances() {
    this.settings.setAliases("alliesgraph", "graphallies");
    this.settings.setDescription("Lists all alliances in the game in the form of a graph.");
    this.settings.setEmbedColor(BotConstants.GENERIC_CMD_COLOR);
    this.settings.setGuildOnly(true);
  }

  @Override
  protected void execute(SlashInput input) {

  }

  protected void execute(MessageInput input) {
    Riskrieg api = new Riskrieg();
    Optional<Game> optGame = api.load(input.event().getGuild().getId(), input.event().getChannel().getId());

    if (hasAnyError(optGame, input)) {
      return;
    }

    //Create the image from the graph
    mxGraph graph = generateAllyGraph(optGame.get());

    mxCircleLayout layout = new mxCircleLayout(graph);
    layout.setDisableEdgeStyle(false);
    layout.execute(graph.getDefaultParent());

    BufferedImage image = mxCellRenderer.createBufferedImage(graph, null, 2, Colors.TERRITORY_COLOR , true, null);
    String filename = "ally-graph";

    EmbedBuilder embedBuilder = new EmbedBuilder();
    embedBuilder.setTitle("Alliances graph");
    embedBuilder.setImage("attachment://" + filename + ".png");
    embedBuilder.setColor(this.settings.getEmbedColor());
    input.event().getChannel().sendMessage(embedBuilder.build()).addFile(ImageUtil.convertToByteArray(image), filename + ".png", new AttachmentOption[0]).queue();
  }

  private mxGraph generateAllyGraph(Game game) {
    Set<Nation> nationList = game.getNations();
    mxGraph graph = new mxGraph();
    
    String borderColour = String.format("%06x", 0xFFFFFF & Colors.BORDER_COLOR.getRGB());
    HashMap<String, Object> vertices = new HashMap<String, Object>();

    //Create the graph by adding the player names as the vertices and then associating them
    for (Nation nation: nationList) {
      String name = game.getPlayer(nation.getLeaderIdentifier().id()).get().getName();
      String colour = String.format("%06x", 0xFFFFFF & nation.getLeaderIdentifier().color().value().getRGB());
      vertices.put(name, graph.insertVertex(
        graph.getDefaultParent(), name, null, 0, 0, 15, 15,
        "ellipse;whiteSpace=wrap;html=1;aspect=fixed;strokeWidth=1;noLabel=1;fillColor=#" + colour + ";strokeColor=#" + borderColour)
      );
    } for (Nation nation: nationList) {
      String colour = String.format("%06x", 0xFFFFFF & nation.getLeaderIdentifier().color().value().getRGB());

      Set<Nation> allies = ((AllianceNation) nation).getAllies().stream()
        .map(ally -> game.getNation(ally.id()).get())
        .collect(Collectors.toSet()
      );
      for (Nation ally: allies) {
        String name = game.getPlayer(nation.getLeaderIdentifier().id()).get().getName();
        String nameAlly = game.getPlayer(ally.getLeaderIdentifier().id()).get().getName();
        String style = "endArrow=none;endFill=0;startArrow=none;strokeWidth=1;curved=1;snapToPoint=1;dashed=1;dashPattern=1;noLabel=1;strokeColor=#" + colour;
        if (((IAlliances) game).allied(ally.getLeaderIdentifier().id(), nation.getLeaderIdentifier().id())) {
          style = "endArrow=none;endFill=0;startArrow=none;strokeWidth=1;curved=1;snapToPoint=1;noLabel=1;strokeColor=#" + borderColour;
        }

        graph.insertEdge(graph.getDefaultParent(), "", null, vertices.get(name), vertices.get(nameAlly), style);
      }
    }
    var vertexStyle = graph.getStylesheet().getDefaultVertexStyle();
    vertexStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
    mxConstants.STYLE_ALIGN = mxConstants.ALIGN_CENTER;
    //If labels in vertices are to be set back on, uncomment this and set noLabel to 0 in style vertices and also increase the width and height
    /*vertexStyle.put(mxConstants.STYLE_LABEL_BACKGROUNDCOLOR, "#e0dad9");
    mxConstants.STYLE_ALIGN = mxConstants.ALIGN_CENTER;
    mxConstants.LABEL_INSET=1;*/ 

    var edgeStyle = graph.getStylesheet().getDefaultEdgeStyle();
    edgeStyle.put(mxConstants.STYLE_EDGE, mxConstants.SHAPE_LINE);
    //edgeStyle.put(mxConstants.STYLE_EDGE, mxConstants.EDGESTYLE_TOPTOBOTTOM);
    edgeStyle.put(mxConstants.STYLE_ENDARROW, "none");

    graph.setAllowDanglingEdges(false);

    return graph;
  }

  private Boolean hasAnyError(Optional<Game> optGame, MessageInput input) {
    if (!optGame.isPresent()) {
      input.event().getChannel().sendMessage(Error.create("You need to create a game before using this command.", this.settings)).queue();
      return true;
    } else if (!(optGame.get() instanceof IAlliances)) {
      input.event().getChannel().sendMessage(Error.create("Invalid game mode.", this.settings)).queue();
      return true;
    } else if (!optGame.get().getGameRule("alliances").isPresent() && !optGame.get().getGameRule("alliances").get().isEnabled()) {
      input.event().getChannel().sendMessage(Error.create("The alliances game rule is disabled.", this.settings)).queue();
      return true;
    } 
    return false;
  }
}
