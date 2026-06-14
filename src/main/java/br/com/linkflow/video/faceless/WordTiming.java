package br.com.linkflow.video.faceless;

/**
 * Tempo de início/fim (em segundos) de uma palavra da narração.
 * Usado para sincronizar as legendas (burn-in) com o áudio.
 */
public record WordTiming(String word, double startSeconds, double endSeconds) {}
