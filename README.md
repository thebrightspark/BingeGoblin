# Binge Goblin

Just a lil guy who wants to look out for Twitch streams of a certain game for you and let you know in a Discord channel
of your choice 🥺

This bot has been specifically designed as a quick solution for a friend so they can be notified about when someone
starts streaming on Twitch of a specified game category on Discord. It will only work for a single Discord server, so
will require to be self-hosted and configured for that server.

## Building

To build the bot for yourself, simple clone the project and run the Gradle task `distTar` or `distZip` depending on
which compressed file type you want.  
From there, find your file in `build/distributions/`, extract to wherever you'd like it to run, and run either the
`BingeGoblin-*/bin/BingeGoblin` on Linux or `BingeGoblin-*/bin/BingeGoblin.bat` on Windows.

## Basic Configuration

Before running the app, make sure you have at least Java 21 installed, and have a `config.yaml` in the same directory as
the above file you run to configure the bot with the following properties:

```yaml
discord:
  token: <Discord Bot Token>
  guildId: <Discord Server ID>

twitch:
  clientId: <Twitch Application ClientId>
  clientSecret: <Twitch Application ClientSecret>
  pollIntervalMins: 5
```

For the `discord.token` property, you'll need to make sure you've created a Discord bot here and grab its token:  
https://discord.com/developers/applications/

For the `twitch.clientId` and `twitch.clientSecret` properties, you'll need to create a Twitch application and grab
those respective values:  
https://dev.twitch.tv/docs/authentication/register-app/

The `discord.guildId` is your Discord server's ID. You can get this by enabling Developer Mode in your Discord user
settings and then right clicking on your server and copying it's server ID.

And finally the `twitch.pollIntervalMins` is simply how often the bot will request streams for the specified game
category. Depending on how many streams there are and how often it polls, this could hit Twitch's API rate limits, so
if you have any issues you may need to increase this value.

## Bot Runtime Configuration

Once you have your bot running, invite the bot into your server with the following permissions:

- View Channels
- Send Messages
- Embed Links

You can use something like [this invite generator](https://discordapi.com/permissions.html#19456) to help create the
invite link.

Once it's there, there's a few commands to use to set it up:

- `/set-game`
    - Sets the Twitch game to look for streams of (use the display name, for example "Minecraft" or "Beat Saber").
- `/set-channel`
    - Sets the Discord server channel to post the streams into.
- `/enabled`
    - Sets whether the regular polling and posting of streams is enabled or not. This is `False` by default so you can
      setup the bot safely before enabling it.
- `/poll-streams`
    - Forces the bot to poll for streams now. This doesn't affect the regularly scheduled polling.
