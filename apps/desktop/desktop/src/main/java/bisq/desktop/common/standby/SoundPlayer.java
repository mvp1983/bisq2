/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.common.standby;

import bisq.common.file.FileUtils;
import bisq.common.threading.ExecutorFactory;
import bisq.desktop.ServiceProvider;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

@Slf4j
class SoundPlayer implements PreventStandbyMode {
    private final String baseDir;
    private volatile boolean isPlaying;
    @Nullable
    private ExecutorService executor;

    SoundPlayer(ServiceProvider serviceProvider) {
        baseDir = serviceProvider.getConfig().getBaseDir().toAbsolutePath().toString();
    }

    public void initialize() {
        if (isPlaying) {
            return;
        }
        isPlaying = true;
        executor = ExecutorFactory.newSingleThreadExecutor("PreventStandbyMode");
        executor.submit(this::playSound);
    }

    public void shutdown() {
        if (!isPlaying) {
            return;
        }
        isPlaying = false;
        if (executor != null) {
            ExecutorFactory.shutdownAndAwaitTermination(executor, 10);
            executor = null;
        }
    }

    private void playSound() {
        try {
            String fileName = "prevent-app-nap-silent-sound.aiff";
            File soundFile = Path.of(baseDir, fileName).toFile();
            if (!soundFile.exists()) {
                FileUtils.resourceToFile(fileName, soundFile);
            }
            AudioInputStream audioInputStream = null;
            SourceDataLine sourceDataLine = null;
            while (isPlaying) {
                try {
                    audioInputStream = AudioSystem.getAudioInputStream(soundFile);
                    sourceDataLine = getSourceDataLine(audioInputStream.getFormat());
                    byte[] tempBuffer = new byte[8192];
                    sourceDataLine.open(audioInputStream.getFormat());
                    sourceDataLine.start();
                    int cnt;
                    while ((cnt = audioInputStream.read(tempBuffer, 0, tempBuffer.length)) != -1 && isPlaying) {
                        if (cnt > 0) {
                            sourceDataLine.write(tempBuffer, 0, cnt);
                        }
                    }
                    sourceDataLine.drain();
                } finally {
                    if (audioInputStream != null) {
                        try {
                            audioInputStream.close();
                        } catch (IOException ignore) {
                        }
                    }
                    if (sourceDataLine != null) {
                        sourceDataLine.drain();
                        sourceDataLine.close();
                    }
                }
            }
        } catch (Exception e) {
            log.error("playSound failed", e);
        }
    }

    private static SourceDataLine getSourceDataLine(AudioFormat audioFormat) throws LineUnavailableException {
        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
        return (SourceDataLine) AudioSystem.getLine(dataLineInfo);
    }
}