package com.crecheconecta.service;

import com.crecheconecta.dto.ConcluirCadastroProfessorRequest;
import com.crecheconecta.entity.ConviteCadastro;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.ConviteCadastroRepository;
import com.crecheconecta.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class CadastroProfessorService {

    private final ConviteCadastroRepository convites;
    private final UsuarioRepository usuarios;
    private final TokenConviteService tokens;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;

    public CadastroProfessorService(ConviteCadastroRepository convites, UsuarioRepository usuarios, TokenConviteService tokens, PasswordEncoder encoder, AuditoriaService auditoria) {

        this.convites = convites;
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    @Transactional
    public DadosConviteProfessor consultar(String token) {

        var convite = buscarDisponivel(token);

        return new DadosConviteProfessor(convite.getNome(), convite.getEmail(), convite.getExpiraEm());
    }

    @Transactional
    public CadastroProfessorResultado concluir(ConcluirCadastroProfessorRequest request) {
        var convite = buscarDisponivel(request.token());

        String nome = request.nome().strip();

        if (nome.isBlank()) {
            throw new RegraNegocioException("Informe o nome do professor.");
        }

        if (request.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new RegraNegocioException("A senha deve possuir no máximo 72 bytes.");
        }

        if (usuarios.existsByEmail(convite.getEmail())) {
            throw new RegraNegocioException("Não foi possível concluir este convite.");
        }

        var professor = new Usuario();
        professor.setNome(nome);
        professor.setEmail(convite.getEmail());
        professor.setTelefone(request.telefone());
        professor.setPerfil(Perfil.PROFESSOR);
        professor.setSenhaHash(
                encoder.encode(request.senha())
        );

        professor.setSituacaoCadastro(SituacaoCadastro.PENDENTE_APROVACAO);
        professor.setAtivo(false);

        usuarios.saveAndFlush(professor);

        Instant agora = Instant.now();

        if (!convite.estaDisponivel(agora)) {
            throw conviteInvalido();
        }

        convite.setConsumidoEm(agora);
        convites.flush();

        auditoria.aposCommit("PROFESSOR_CADASTRO_CONCLUIDO", professor.getId(), professor.getId());

        return new CadastroProfessorResultado(professor.getId(), "Cadastro concluído. Aguarde a aprovação da direção.");
    }

    private ConviteCadastro buscarDisponivel(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw conviteInvalido();
        }

        String hash = tokens.calcularHash(token);

        var convite = convites.buscarPorTokenHashComBloqueio(hash)
                .orElseThrow(this::conviteInvalido);

        if (convite.getPerfil() != Perfil.PROFESSOR || !convite.estaDisponivel(Instant.now())) {
            throw conviteInvalido();
        }

        return convite;
    }

    private RegraNegocioException conviteInvalido() {

        return new RegraNegocioException(
                "Convite inválido, expirado, cancelado ou já utilizado."
        );
    }

    public record DadosConviteProfessor(String nome, String email, Instant expiraEm) {
    }

    public record CadastroProfessorResultado(UUID usuarioId, String mensagem) {
    }
}