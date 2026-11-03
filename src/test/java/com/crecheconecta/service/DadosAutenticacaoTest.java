package com.crecheconecta.service;

import com.crecheconecta.entity.AcaoVerificacao;
import com.crecheconecta.entity.ConviteCadastro;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;

import java.time.Instant;
import java.util.UUID;

final class DadosAutenticacaoTest {

    static final String TOKEN = "A".repeat(43);
    static final String HASH = "b".repeat(64);
    static final String SENHA = "SenhaSegura123!";
    static final String EMAIL = "pessoa@example.test";

    private DadosAutenticacaoTest() {
    }

    static Usuario usuario(Perfil perfil) {
        var usuario = new Usuario();

        usuario.setId(UUID.randomUUID());
        usuario.setNome("Pessoa de teste");
        usuario.setEmail(EMAIL);
        usuario.setSenhaHash("hash-antigo");
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        usuario.setSituacaoCadastro(SituacaoCadastro.APROVADO);
        usuario.setVersao(0L);

        return usuario;
    }

    static AcaoVerificacao acao(
            Usuario usuario,
            FinalidadeAcao finalidade
    ) {
        var acao = new AcaoVerificacao();

        acao.setUsuario(usuario);
        acao.setFinalidade(finalidade);
        acao.setSegredoHash(HASH);
        acao.setCriadoEm(Instant.now().minusSeconds(120));
        acao.setExpiraEm(Instant.now().plusSeconds(3600));

        return acao;
    }

    static ConviteCadastro convite(Perfil perfil) {
        var convite = new ConviteCadastro();

        convite.setNome("Pessoa convidada");
        convite.setEmail(EMAIL);
        convite.setPerfil(perfil);
        convite.setCriadoPor(usuario(Perfil.DIRECAO));
        convite.setTokenHash(HASH);
        convite.setCriadoEm(Instant.now().minusSeconds(120));
        convite.setExpiraEm(Instant.now().plusSeconds(3600));

        return convite;
    }
}