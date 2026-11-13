package com.crecheconecta.controller;

import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chat;

    public ChatController(ChatService chat) {

        this.chat = chat;
    }

    @GetMapping("/contatos")
    public ResponseEntity<ChatService.PaginaContatos> contatos(@AuthenticationPrincipal UsuarioAutenticado usuario, @RequestParam(defaultValue = "") String busca, @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "false") boolean somenteConversas) {

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chat.contatos(UsuarioAtual.de(usuario), busca, pagina, somenteConversas));
    }

    @GetMapping("/contatos/{contato}/mensagens")
    public ResponseEntity<ChatService.PaginaMensagens> mensagens(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID contato, @RequestParam(required = false) Long antes) {

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chat.mensagens(UsuarioAtual.de(usuario), contato, antes));
    }

    @PostMapping("/contatos/{contato}/mensagens")
    public ResponseEntity<ChatService.Mensagem> enviar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID contato, @Valid @RequestBody EnviarMensagem request) {

        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(chat.enviar(UsuarioAtual.de(usuario), contato, request.texto()));
    }

    public record EnviarMensagem(@NotBlank @Size(max = 2000) String texto) {}
}