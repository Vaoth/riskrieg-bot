package com.riskrieg.bot.core.commands.running;

import com.riskrieg.api.Riskrieg;
import com.riskrieg.bot.core.Command;
import com.riskrieg.bot.core.input.MessageInput;
import com.riskrieg.bot.util.Error;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.constant.Colors;
import com.riskrieg.constant.Constants;
import com.riskrieg.gamemode.Game;
import com.riskrieg.gamemode.IAlliances;
import com.riskrieg.player.Player;
import com.riskrieg.player.PlayerColor;
import com.riskrieg.response.Response;
import java.time.Instant;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;

public class Ally extends Command {

  public Ally() {
    this.settings.setAliases("ally", "accept");
    this.settings.setDescription("Allies with a player.");
    this.settings.setEmbedColor(Colors.BORDER_COLOR);
    this.settings.setGuildOnly(true);
  }

  protected void execute(MessageInput input) { // TODO: Check win status, etc.
    Riskrieg api = new Riskrieg();
    Optional<Game> optGame = api.load(input.event().getGuild().getId(), input.event().getChannel().getId());
    if (optGame.isPresent()) {
      Game game = optGame.get();
      Optional<Player> optPlayer = game.getPlayer(input.event().getMember().getId());
      Optional<Player> optTwo = getSecondPlayer(game, input);
      if (optPlayer.isPresent() && optTwo.isPresent()) {
        if (game instanceof IAlliances) {
          Response response = ((IAlliances) game).ally(optPlayer.get().getID(), optTwo.get().getID());
          if (response.success()) {
            EmbedBuilder embedBuilder = settings.embedBuilder();
            if (((IAlliances) game).allied(optPlayer.get().getID(), optTwo.get().getID())) {
              embedBuilder.setTitle("Alliance Formed");
              embedBuilder.setDescription(optPlayer.get().getName() + " and " + optTwo.get().getName() + " have formed an alliance.");
            } else {
              embedBuilder.setTitle("Alliance Request Sent");
              embedBuilder.setDescription(optPlayer.get().getName() + " has sent an alliance request to " + optTwo.get().getName() + ".");
            }
            embedBuilder.setFooter("Version: " + Constants.VERSION);
            embedBuilder.setTimestamp(Instant.now());

            Response updateResponse = game.update();
            if (updateResponse.success() && updateResponse.getMessage().isPresent() && !updateResponse.getMessage().get().isBlank()) {
              embedBuilder.addField("Update", updateResponse.getMessage().get(), false);
            }

            if (game.isEnded()) {
              api.delete(input.event().getGuild().getId(), input.event().getChannel().getId());
            } else {
              input.event().getChannel().sendMessage(embedBuilder.build()).queue();
              api.save(game, input.event().getGuild().getId(), input.event().getChannel().getId());
            }
          } else {
            input.event().getChannel().sendMessage(Error.create(response, this.settings)).queue();
          }
        } else {
          input.event().getChannel().sendMessage(Error.create("Invalid game mode.", this.settings)).queue();
        }
      } else {
        input.event().getChannel().sendMessage(Error.create("Player is not in the game.", this.settings)).queue();
      }
    } else {
      input.event().getChannel().sendMessage(Error.create("You need to create a game before using this command.", this.settings)).queue();
    }
  }

  private Optional<Player> getSecondPlayer(Game game, MessageInput input) {
    Optional<PlayerColor> optColor = MessageUtil.parseColor(input.argString());
    if (optColor.isPresent()) {
      return game.getPlayer(optColor.get());
    } else if (input.event().getMessage().getMentionedMembers().size() == 1) {
      return game.getPlayer(input.event().getMessage().getMentionedMembers().get(0).getId());
    }
    return Optional.empty();
  }

}
