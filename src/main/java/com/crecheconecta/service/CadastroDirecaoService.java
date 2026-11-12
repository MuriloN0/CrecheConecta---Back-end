package com.crecheconecta.service;

import com.crecheconecta.dto.ConcluirCadastroDirecaoRequest;
import com.crecheconecta.dto.ConvidarDirecaoRequest;
import com.crecheconecta.dto.ConviteCadastroResponse;
import com.crecheconecta.entity.ConviteCadastro;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.ConviteCadastroRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class CadastroDirecaoService {

    private final UsuarioRepository usuarios;
    private final ConviteCadastroRepository convites;
    private final TokenConviteService tokens;
    private final PasswordEncoder encoder;
    private final EmailService emails;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final TransactionTemplate transacao;
    private final String urlCadastro;

    public CadastroDirecaoService(
            UsuarioRepository usuarios,
            ConviteCadastroRepository convites,
            TokenConviteService tokens,
            PasswordEncoder encoder,
            EmailService emails,
            AuditoriaService auditoria,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${app.cadastro.direcao.url}") String urlCadastro) {

        this.usuarios = usuarios;
        this.convites = convites;
        this.tokens = tokens;
        this.encoder = encoder;
        this.emails = emails;
        this.auditoria = auditoria;
        this.clock = clock;
        this.transacao = new TransactionTemplate(transactionManager);
        this.urlCadastro = validarUrl(urlCadastro);
    }

    @PreAuthorize("hasRole('DIRECAO')")
    public ConviteCadastroResponse convidar(ConvidarDirecaoRequest request, UsuarioAtual solicitante) {

        validarSolicitante(solicitante);

        var envio = Objects.requireNonNull(transacao.execute(status -> prepararConvite(request, solicitante)));

        try {

            emails.enviarConviteDirecao(envio.id(), envio.email(), envio.link());
        } catch (RuntimeException falhaEnvio) {

            try {
                cancelarConvite(envio.id());
            } catch (RuntimeException falhaCancelamento) {

                falhaEnvio.addSuppressed(falhaCancelamento);
            }

            throw falhaEnvio;
        }

        return new ConviteCadastroResponse(envio.id(), envio.email(), envio.expiraEm(), "Convite enviado. O destinatário deve concluir " + "o cadastro pelo e-mail.");
    }

    public DadosConviteDirecao consultar(String token) {

        return Objects.requireNonNull(transacao.execute(status -> {

                    var convite = buscarDisponivel(token);

                    return new DadosConviteDirecao(
                            convite.getNome(),
                            convite.getEmail(),
                            convite.getExpiraEm()
                    );
                })
        );
    }

    public CadastroDirecaoResultado concluir(ConcluirCadastroDirecaoRequest request) {

        return Objects.requireNonNull(transacao.execute(status -> concluirCadastro(request)));
    }

    private EnvioConvite prepararConvite(ConvidarDirecaoRequest request, UsuarioAtual solicitante) {

        var diretor = usuarios.buscarPorIdComBloqueio(solicitante.id()).orElseThrow(this::acessoNegado);

        if (!direcaoAtiva(diretor)) {

            throw acessoNegado();
        }

        String nome = request.nome().strip();
        String email = request.email().strip().toLowerCase(Locale.ROOT);

        if (nome.isBlank()) {

            throw new RegraNegocioException("Informe o nome do integrante da direção.");
        }

        if (usuarios.existsByEmail(email)) {

            throw new RegraNegocioException("Já existe um usuário cadastrado com este e-mail.");
        }

        var agora = clock.instant();

        var anterior = convites.buscarPendentePorEmailComBloqueio(email).orElse(null);

        if (anterior != null) {

            if (anterior.getPerfil() != Perfil.DIRECAO) {

                throw new RegraNegocioException("Já existe um convite pendente para outro " + "perfil neste e-mail.");
            }

            if (anterior.getCriadoEm().plusSeconds(60).isAfter(agora)) {

                throw new RegraNegocioException("Aguarde 60 segundos antes de reenviar o convite.");
            }

            anterior.setCanceladoEm(agora);
            convites.flush();
        }

        String token = tokens.gerarToken();

        var convite = new ConviteCadastro();
        convite.setNome(nome);
        convite.setEmail(email);
        convite.setPerfil(Perfil.DIRECAO);
        convite.setTokenHash(tokens.calcularHash(token));
        convite.setCriadoPor(diretor);
        convite.setCriadoEm(agora);
        convite.setExpiraEm(agora.plus(Duration.ofHours(24)));

        convites.saveAndFlush(convite);

        auditoria.aposCommit("CONVITE_DIRECAO_CRIADO", diretor.getId(), convite.getId());

        return new EnvioConvite(convite.getId(), email, convite.getExpiraEm(), urlCadastro + "#token=" + token);
    }

    private CadastroDirecaoResultado concluirCadastro(ConcluirCadastroDirecaoRequest request) {

        var convite = buscarDisponivel(request.token());

        validarDadosCadastro(request);

        if (usuarios.existsByEmail(convite.getEmail())) {

            throw conviteInvalido();
        }

        var usuario = new Usuario();
        usuario.setNome(convite.getNome());
        usuario.setEmail(convite.getEmail());
        usuario.setTelefone(request.telefone());
        usuario.setSenhaHash(encoder.encode(request.senha()));
        usuario.setPerfil(Perfil.DIRECAO);
        usuario.setSituacaoCadastro(SituacaoCadastro.APROVADO);
        usuario.setAtivo(true);

        var agora = clock.instant();

        if (!convite.estaDisponivel(agora)) {

            throw conviteInvalido();
        }

        usuarios.saveAndFlush(usuario);

        convite.setConsumidoEm(agora);
        convites.flush();

        auditoria.aposCommit("DIRECAO_CADASTRO_CONCLUIDO", usuario.getId(), usuario.getId());

        return new CadastroDirecaoResultado(usuario.getId(), "Cadastro concluído. Você já pode fazer login.");
    }

    private ConviteCadastro buscarDisponivel(String token) {

        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {

            throw conviteInvalido();
        }

        var convite = convites.buscarPorTokenHashComBloqueio(tokens.calcularHash(token)).orElseThrow(this::conviteInvalido);

        if (convite.getPerfil() != Perfil.DIRECAO || !convite.estaDisponivel(clock.instant())) {

            throw conviteInvalido();
        }

        return convite;
    }

    private void validarDadosCadastro(
            ConcluirCadastroDirecaoRequest request
    ) {
        if (request.telefone() == null || !request.telefone().matches("[1-9][0-9]{9,10}")) {

            throw new RegraNegocioException("Informe o telefone com DDD e somente números.");
        }

        String senha = request.senha();

        if (senha == null || senha.isBlank() || senha.length() < 12 || senha.getBytes(StandardCharsets.UTF_8).length > 72) {

            throw new RegraNegocioException("A senha deve ter pelo menos 12 caracteres " + "e no máximo 72 bytes.");
        }
    }

    private void cancelarConvite(UUID conviteId) {
        transacao.executeWithoutResult(status -> {

            var convite = convites.buscarPorIdComBloqueio(conviteId).orElse(null);

            if (convite != null && convite.getConsumidoEm() == null && convite.getCanceladoEm() == null) {

                convite.setCanceladoEm(clock.instant());
                convites.flush();
            }
        });
    }

    private void validarSolicitante(UsuarioAtual solicitante) {
        if (solicitante == null
                || solicitante.id() == null
                || solicitante.perfil() != Perfil.DIRECAO) {
            throw acessoNegado();
        }
    }

    private boolean direcaoAtiva(Usuario usuario) {

        return usuario.getPerfil() == Perfil.DIRECAO && usuario.isAtivo() && usuario.getSituacaoCadastro() == SituacaoCadastro.APROVADO;
    }

    private AccessDeniedException acessoNegado() {

        return new AccessDeniedException("Acesso restrito à direção ativa e aprovada.");
    }

    private RegraNegocioException conviteInvalido() {

        return new RegraNegocioException("Convite inválido, expirado, cancelado ou já utilizado.");
    }

    private String validarUrl(String valor) {

        URI uri = URI.create(valor);

        boolean https = "https".equalsIgnoreCase(uri.getScheme());

        boolean httpLocal = "http".equalsIgnoreCase(uri.getScheme()) && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));

        if ((!https && !httpLocal) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {

            throw new IllegalArgumentException("app.cadastro.direcao.url deve usar HTTPS " + "ou HTTP local, sem credenciais, " + "query ou fragmento.");
        }

        return uri.toASCIIString();
    }

    private record EnvioConvite(UUID id, String email, Instant expiraEm, String link) {

        @Override
        public String toString() {

            return "EnvioConvite[dados omitidos]";
        }
    }

    public record DadosConviteDirecao(String nome, String email, Instant expiraEm) {
    }

    public record CadastroDirecaoResultado(UUID usuarioId, String mensagem) {
    }
}