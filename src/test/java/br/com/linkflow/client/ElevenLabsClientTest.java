package br.com.linkflow.client;

import br.com.linkflow.video.faceless.WordTiming;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ElevenLabsClientTest {

    @Test
    @DisplayName("Deve converter alinhamento por caractere em tempos por palavra")
    void deveConverterCharsEmPalavras() {
        // "Oi mundo"
        List<String> chars = List.of("O", "i", " ", "m", "u", "n", "d", "o");
        List<Double> starts = List.of(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7);
        List<Double> ends = List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8);

        List<WordTiming> words = ElevenLabsClient.charsToWords(chars, starts, ends);

        assertThat(words).hasSize(2);
        assertThat(words.get(0)).isEqualTo(new WordTiming("Oi", 0.0, 0.2));
        assertThat(words.get(1)).isEqualTo(new WordTiming("mundo", 0.3, 0.8));
    }

    @Test
    @DisplayName("Deve ignorar espaços extras e lidar com listas vazias")
    void deveLidarComEspacosEVazio() {
        assertThat(ElevenLabsClient.charsToWords(List.of(), List.of(), List.of())).isEmpty();

        List<String> chars = List.of(" ", "a", " ", " ", "b", " ");
        List<Double> starts = List.of(0.0, 0.1, 0.2, 0.3, 0.4, 0.5);
        List<Double> ends = List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6);

        List<WordTiming> words = ElevenLabsClient.charsToWords(chars, starts, ends);
        assertThat(words).extracting(WordTiming::word).containsExactly("a", "b");
    }
}