/*
 *     Copyright 2015-2017 Austin Keener & Michael Ritter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.handle;

import net.dv8tion.jda.Core;
import net.dv8tion.jda.audio.AudioConnection;
import net.dv8tion.jda.audio.AudioWebSocket;
import net.dv8tion.jda.manager.AudioManager;
import org.json.JSONObject;

public class VoiceServerUpdateHandler
{
    final Core core;

    public VoiceServerUpdateHandler(Core core)
    {
        this.core = core;
    }


    public Long handle(String sessionId, JSONObject content)
    {
        if (sessionId == null || sessionId.isEmpty())
            throw new IllegalArgumentException("Provided session id was null or empty!");

        final String guildId = content.getString("guild_id");
        core.getConnectionManager().getQueuedAudioConnectionMap().remove(guildId);

        if (content.isNull("endpoint"))
        {
            //Discord did not provide an endpoint yet, we are to wait until discord has resources to provide
            // an endpoint, which will result in them sending another VOICE_SERVER_UPDATE which we will handle
            // to actually connect to the audio server.
            return null;
        }

        String endpoint = content.getString("endpoint");
        String token = content.getString("token");
        if (guildId == null)
            throw new IllegalArgumentException("Attempted to start audio connection with Guild that doesn't exist! JSON: " + content);

        //Strip the port from the endpoint.
        endpoint = endpoint.replace(":80", "");

        AudioManager audioManager = core.getAudioManager(guildId);
        synchronized (audioManager.CONNECTION_LOCK) //Synchronized to prevent attempts to close while setting up initial objects.
        {
            if (audioManager.isConnected())
                audioManager.prepareForRegionChange();
            if (!audioManager.isAttemptingToConnect())
            {
                Core.LOG.debug("Received a VOICE_SERVER_UPDATE but JDA is not currently connected nor attempted to connect " +
                        "to a VoiceChannel. Assuming that this is caused by another client running on this account. Ignoring the event.");
                return null;
            }

            AudioWebSocket socket = new AudioWebSocket(audioManager.getListenerProxy(), endpoint, core, guildId, sessionId, token, audioManager.isAutoReconnect());
            AudioConnection connection = new AudioConnection(socket, audioManager.getQueuedAudioConnectionId());
            audioManager.setAudioConnection(connection);
            socket.startConnection();

            return null;
        }
    }
}
