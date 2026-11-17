package com.crecheconecta.controller;

import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.ChatModeracaoService;
import com.crecheconecta.service.ChatService;
import com.crecheconecta.service.FiltroLinguagem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;
    private final ChatModeracaoService moderacao;

    public ChatController(ChatService chat, ChatModeracaoService moderacao) {
        this.chat = chat;
        this.moderacao = moderacao;
    }

    @GetMapping("/contatos")
    public ResponseEntity<ChatService.PaginaContatos> contatos(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(defaultValue = "") String busca,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "false") boolean somenteConversas
    ) {
        var resposta = chat.contatos(UsuarioAtual.de(usuario), busca, pagina, somenteConversas);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/contatos/{contato}/mensagens")
    public ResponseEntity<ChatService.PaginaMensagens> mensagens(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID contato,
            @RequestParam(required = false) Long antes
    ) {
        var resposta = chat.mensagens(UsuarioAtual.de(usuario), contato, antes);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping(value = "/contatos/{contato}/mensagens", params = "!favorito")
    @Transactional
    public ResponseEntity<ChatService.Mensagem> enviar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID contato,
            @Valid @RequestBody EnviarMensagem request
    ) {
        var remetente = UsuarioAtual.de(usuario);
        FiltroLinguagem.validarMensagem(request.texto());
        moderacao.validarEnvio(remetente);

        var resposta = chat.enviar(remetente, contato, request.texto());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(resposta);
    }

    @PostMapping(value = "/contatos/{contato}/mensagens", params = "favorito")
    public ResponseEntity<Void> alterarFavorito(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID contato,
            @RequestParam("favorito") UUID mensagem,
            @Valid @RequestBody AlterarFavorito request
    ) {
        boolean alterada = chat.alterarFavorito(
                UsuarioAtual.de(usuario), contato, mensagem, request.favorita()
        );

        return ResponseEntity.status(alterada ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.noStore())
                .build();
    }

    public record EnviarMensagem(
            @NotBlank
            @Size(max = 4000, message = "Escreva uma mensagem de até 4.000 caracteres.")
            String texto
    ) {}

    public record AlterarFavorito(@NotNull Boolean favorita) {}
}