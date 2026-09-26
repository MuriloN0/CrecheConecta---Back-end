package com.crecheconecta.service;


import com.crecheconecta.dto.LoginRequest;
import com.crecheconecta.dto.LoginResponse;
import com.crecheconecta.entity.AcaoVerificacao;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import com.crecheconecta.repository.AcaoVerificacaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.SegredoVerificacaoService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final AcaoVerificacaoRepository acoes;
    private final PasswordEncoder encoder;
    private final SegredoVerificacaoService segredos;
    private final EmailService emailService;
    private final TransactionTemplate transacao;

    private final String hashFicticio;

    public AuthService(
            UsuarioRepository usuarios,
            AcaoVerificacaoRepository acoes,
            PasswordEncoder encoder,
            SegredoVerificacaoService segredos,
            EmailService emailService,
            PlatformTransactionManager transactionManager
    ) {
        this.usuarios = usuarios;
        this.acoes = acoes;
        this.encoder = encoder;
        this.segredos = segredos;
        this.emailService = emailService;
        this.transacao = new TransactionTemplate(transactionManager);

        this.hashFicticio = encoder.encode(
                UUID.randomUUID().toString()
        );
    }

    public LoginResponse iniciarLogin(LoginRequest request) {
        var resultado = Objects.requireNonNull(
                transacao.execute(status -> prepararLogin(request))
        );

        if (resultado.erro() != null) {
            throw new AutenticacaoException(resultado.erro());
        }

        var envio = resultado.envio();

        try {
            emailService.enviarCodigo(
                    envio.acaoId(),
                    envio.email(),
                    envio.codigo(),
                    FinalidadeAcao.LOGIN
            );
        } catch (AutenticacaoException exception) {
            invalidarAposFalhaDeEnvio(
                    envio.usuarioId(),
                    envio.acaoId()
            );

            throw exception;
        }

        return new LoginResponse(
                envio.acaoId(),
                envio.expiraEm(),
                "Código enviado. Confira seu e-mail."
        );
    }

    private Resultado prepararLogin(LoginRequest request) {
        String email = request.email()
                .strip()
                .toLowerCase(Locale.ROOT);

        boolean senhaDentroDoLimite =
                request.senha()
                        .getBytes(StandardCharsets.UTF_8)
                        .length <= 72;

        var usuario = usuarios
                .buscarPorEmailComBloqueio(email)
                .orElse(null);

        if (usuario == null) {
            encoder.matches(
                    senhaDentroDoLimite ? request.senha() : "",
                    hashFicticio
            );

            return falha(ErroAutenticacao.CREDENCIAIS_INVALIDAS);
        }

        Instant agora = Instant.now();

        if (!usuario.isAtivo()
                || (usuario.getBloqueadoAte() != null
                && usuario.getBloqueadoAte().isAfter(agora))) {
            return falha(ErroAutenticacao.CREDENCIAIS_INVALIDAS);
        }

        if (usuario.getBloqueadoAte() != null) {
            usuario.setBloqueadoAte(null);
            usuario.setTentativas(0);
        }

        boolean senhaCorreta = senhaDentroDoLimite
                && encoder.matches(
                request.senha(),
                usuario.getSenhaHash()
        );

        if (!senhaCorreta) {
            usuario.setTentativas(usuario.getTentativas() + 1);

            if (usuario.getTentativas() >= 5) {
                usuario.setBloqueadoAte(agora.plusSeconds(900));
            }

            return falha(ErroAutenticacao.CREDENCIAIS_INVALIDAS);
        }

        var ultimaAcao = acoes
                .findFirstByUsuario_IdAndFinalidadeOrderByCriadoEmDesc(
                        usuario.getId(),
                        FinalidadeAcao.LOGIN
                );

        if (ultimaAcao.isPresent()
                && ultimaAcao.get()
                .getCriadoEm()
                .plusSeconds(60)
                .isAfter(agora)) {
            return falha(ErroAutenticacao.LIMITE_TENTATIVAS);
        }

        usuario.setTentativas(0);
        usuario.setBloqueadoAte(null);

        for (var anterior : acoes.buscarPendentes(
                usuario.getId(),
                FinalidadeAcao.LOGIN
        )) {
            anterior.setInvalidadoEm(agora);
        }

        String codigo = segredos.gerarCodigo();

        var acao = new AcaoVerificacao();
        acao.setUsuario(usuario);
        acao.setFinalidade(FinalidadeAcao.LOGIN);
        acao.setCriadoEm(agora);
        acao.setExpiraEm(agora.plusSeconds(300));
        acao.setTentativas(0);

        acao.setSegredoHash(
                segredos.proteger(
                        usuario.getId(),
                        acao.getId(),
                        FinalidadeAcao.LOGIN,
                        codigo
                )
        );

        acoes.save(acao);

        return new Resultado(
                null,
                new EnvioPendente(
                        usuario.getId(),
                        acao.getId(),
                        usuario.getEmail(),
                        codigo,
                        acao.getExpiraEm()
                )
        );
    }

    private void invalidarAposFalhaDeEnvio(
            UUID usuarioId,
            UUID acaoId
    ) {
        transacao.executeWithoutResult(status -> {
            usuarios.buscarPorIdComBloqueio(usuarioId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Usuário da ação não encontrado."
                    ));

            acoes.buscarPorIdComBloqueio(acaoId)
                    .ifPresent(acao -> {
                        if (acao.getConsumidoEm() == null
                                && acao.getInvalidadoEm() == null) {
                            acao.setInvalidadoEm(Instant.now());
                        }
                    });
        });
    }

    private Resultado falha(ErroAutenticacao erro) {
        return new Resultado(erro, null);
    }

    private record Resultado(
            ErroAutenticacao erro,
            EnvioPendente envio
    ) {
    }

    private record EnvioPendente(
            UUID usuarioId,
            UUID acaoId,
            String email,
            String codigo,
            Instant expiraEm
    ) {
        @Override
        public String toString() {
            return "EnvioPendente[dados omitidos]";
        }
    }

}
