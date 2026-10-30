package com.crecheconecta.controller.sessao;

import com.crecheconecta.controller.base.IntegracaoTestBase;
import com.crecheconecta.dto.UsuarioLogadoResponse;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Sessao;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.service.TokenSessaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessaoControllerIntegrationTest extends IntegracaoTestBase {

    private static final String ME_PATH = "/api/auth/me";
    private static final String LOGOUT_PATH = "/api/auth/logout";

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private TokenSessaoService tokenSessaoService;

    @Test
    void me_comLoginValido_deveRetornarDadosDoUsuario() {
        Usuario usuario = criarUsuario(Perfil.RESPONSAVEL);
        String token = autenticar(usuario);

        ResponseEntity<UsuarioLogadoResponse> response = client.get().uri(ME_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toEntity(UsuarioLogadoResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        UsuarioLogadoResponse body = response.getBody();
        assertThat(body.id()).isEqualTo(usuario.getId());
        assertThat(body.nome()).isEqualTo(usuario.getNome());
        assertThat(body.email()).isEqualTo(usuario.getEmail());
        assertThat(body.perfil()).isEqualTo(Perfil.RESPONSAVEL);
    }

    @Test
    void me_semLogin_deveRetornar401() {
        ResponseEntity<Map<String, Object>> response = client.get().uri(ME_PATH)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getBody().get("codigo")).isEqualTo("NAO_AUTENTICADO");
        assertThat(response.getBody().get("detail")).isEqualTo("Autenticação necessária ou sessão inválida.");
    }

    @Test
    void me_comTokenFalso_deveRetornar401() {
        String tokenDesconhecido = tokenSessaoService.gerar();

        ResponseEntity<Map<String, Object>> response = client.get().uri(ME_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDesconhecido))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("codigo")).isEqualTo("NAO_AUTENTICADO");
    }

    @Test
    void me_semPrefixoBearer_deveRetornar401() {
        String token = autenticar(criarUsuario(Perfil.PROFESSOR));

        ResponseEntity<Map<String, Object>> response = client.get().uri(ME_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + token)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("codigo")).isEqualTo("NAO_AUTENTICADO");
    }

    @Test
    void logout_comLoginValido_deveEncerrarSessao() {
        String token = autenticar(criarUsuario(Perfil.PROFESSOR));
        String hash = tokenSessaoService.calcularHash(token);
        assertThat(sessaoRepository.findByTokenHash(hash).orElseThrow().getRevogadoEm()).isNull();

        ResponseEntity<Void> response = client.post().uri(LOGOUT_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        Sessao sessao = sessaoRepository.findByTokenHash(hash).orElseThrow();
        assertThat(sessao.getRevogadoEm()).isNotNull();
    }

    @Test
    void me_aposLogout_deveRetornar401() {
        String token = autenticar(criarUsuario(Perfil.DIRECAO));
        client.post().uri(LOGOUT_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toBodilessEntity();

        ResponseEntity<Map<String, Object>> response = client.get().uri(ME_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("codigo")).isEqualTo("NAO_AUTENTICADO");
    }

    @Test
    void logout_semLogin_deveRetornar401() {
        ResponseEntity<Map<String, Object>> response = client.post().uri(LOGOUT_PATH)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("codigo")).isEqualTo("NAO_AUTENTICADO");
    }

    @Test
    void logout_deUmUsuario_naoDeveDerrubarOutroUsuario() {
        String tokenQueSai = autenticar(criarUsuario(Perfil.PROFESSOR));
        Usuario outroUsuario = criarUsuario(Perfil.RESPONSAVEL);
        String tokenQueFica = autenticar(outroUsuario);

        client.post().uri(LOGOUT_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenQueSai))
                .retrieve()
                .toBodilessEntity();
        ResponseEntity<UsuarioLogadoResponse> response = client.get().uri(ME_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenQueFica))
                .retrieve()
                .toEntity(UsuarioLogadoResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(outroUsuario.getId());
        assertThat(sessaoRepository.findByTokenHash(tokenSessaoService.calcularHash(tokenQueFica))
                .orElseThrow().getRevogadoEm()).isNull();
    }
}
