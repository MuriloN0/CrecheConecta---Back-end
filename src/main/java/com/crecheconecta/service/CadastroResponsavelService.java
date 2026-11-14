package com.crecheconecta.service;

import com.crecheconecta.dto.ConcluirCadastroResponsavelRequest;
import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.AlunoResponsavel;
import com.crecheconecta.entity.ConviteCadastro;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.AlunoResponsavelRepository;
import com.crecheconecta.repository.ConviteCadastroRepository;
import com.crecheconecta.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class CadastroResponsavelService {

    private static final ZoneId FUSO_CRECHE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DATA_NASCIMENTO_MINIMA = LocalDate.of(1900, 1, 1);

    private final ConviteCadastroRepository convites;
    private final UsuarioRepository usuarios;
    private final AlunoRepository alunos;
    private final AlunoResponsavelRepository vinculos;
    private final TokenConviteService tokens;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final TermoService termos;

    public CadastroResponsavelService(ConviteCadastroRepository convites, UsuarioRepository usuarios, AlunoRepository alunos, AlunoResponsavelRepository vinculos, TokenConviteService tokens, PasswordEncoder encoder, AuditoriaService auditoria, Clock clock, TermoService termos) {

        this.convites = convites;
        this.usuarios = usuarios;
        this.alunos = alunos;
        this.vinculos = vinculos;
        this.tokens = tokens;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.clock = clock;
        this.termos = termos;
    }

    @Transactional
    public DadosConviteResponsavel consultar(String token) {

        var convite = buscarDisponivel(token);
        return new DadosConviteResponsavel(convite.getNome(), convite.getEmail(), convite.getExpiraEm(), termos.documentosCadastro());
    }

    @Transactional
    public CadastroResponsavelResultado concluir(ConcluirCadastroResponsavelRequest request) {

        var convite = buscarDisponivel(request.token());

        if (usuarios.existsByEmail(convite.getEmail())) {

            throw new RegraNegocioException("Não foi possível concluir este convite.");
        }

        if (request.senha().getBytes(StandardCharsets.UTF_8).length > 72) {

            throw new RegraNegocioException("A senha deve possuir no máximo 72 bytes.");
        }

        LocalDate dataNascimento = request.dataNascimento();
        LocalDate hoje = LocalDate.now(clock.withZone(FUSO_CRECHE));

        if (dataNascimento == null || dataNascimento.isBefore(DATA_NASCIMENTO_MINIMA) || dataNascimento.isAfter(hoje)) {

            throw new RegraNegocioException("Informe uma data de nascimento válida até hoje.");
        }

        if (!Boolean.TRUE.equals(request.aceitouDocumentos())) {

            throw new RegraNegocioException("Aceite os Termos de Uso e a Política de Privacidade.");
        }

        var responsavel = new Usuario();
        responsavel.setNome(request.nome().strip());
        responsavel.setEmail(convite.getEmail());
        responsavel.setTelefone(request.telefone());
        responsavel.setSenhaHash(encoder.encode(request.senha()));
        responsavel.setPerfil(Perfil.RESPONSAVEL);
        responsavel.setSituacaoCadastro(SituacaoCadastro.APROVADO);
        responsavel.setAtivo(true);
        usuarios.saveAndFlush(responsavel);

        var aluno = new Aluno();
        aluno.setNome(request.nomeAluno().strip());
        aluno.setDataNascimento(dataNascimento);
        alunos.saveAndFlush(aluno);

        Instant agora = clock.instant();
        if (!convite.estaDisponivel(agora)) {

            throw conviteInvalido();
        }

        var vinculo = new AlunoResponsavel();
        vinculo.setAlunoId(aluno.getId());
        vinculo.setResponsavelId(responsavel.getId());
        vinculo.setCriadoEm(agora);
        vinculos.saveAndFlush(vinculo);

        termos.registrarAceiteCadastro(responsavel.getId(), aluno.getId(), request.versaoTermos());

        convite.setConsumidoEm(agora);
        convites.flush();

        auditoria.aposCommit("RESPONSAVEL_CADASTRO_CONCLUIDO", responsavel.getId(), aluno.getId());

        return new CadastroResponsavelResultado(responsavel.getId(), aluno.getId(), "Cadastro concluído. Você já pode entrar.");
    }

    private ConviteCadastro buscarDisponivel(String token) {

        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {

            throw conviteInvalido();
        }

        var convite = convites.buscarPorTokenHashComBloqueio(tokens.calcularHash(token)).orElseThrow(this::conviteInvalido);

        if (convite.getPerfil() != Perfil.RESPONSAVEL || !convite.estaDisponivel(clock.instant())) {

            throw conviteInvalido();
        }

        return convite;
    }

    private RegraNegocioException conviteInvalido() {

        return new RegraNegocioException("Convite inválido, expirado, cancelado ou já utilizado.");
    }

    public record DadosConviteResponsavel(String nome, String email, Instant expiraEm, TermoService.DocumentosCadastro documentos) {

    }

    public record CadastroResponsavelResultado(UUID usuarioId, UUID alunoId, String mensagem) {}
}