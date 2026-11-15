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
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@Service
public class CadastroResponsavelService {

    private static final ZoneId FUSO_CRECHE = ZoneId.of("America/Sao_Paulo");
    private static final int IDADE_MINIMA_MESES = 6;
    private static final int IDADE_MAXIMA_ANOS = 4;

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

        return new DadosConviteResponsavel(convite.getNome(), convite.getEmail(), convite.getExpiraEm(), termos.documentosCadastro(), faixaNascimento());
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
        var faixa = faixaNascimento();

        if (dataNascimento == null) {

            throw new RegraNegocioException("Informe a data de nascimento do aluno.");
        }

        if (dataNascimento.isBefore(faixa.dataMinima())) {

            throw new RegraNegocioException(
                    "O aluno deve ter no máximo 4 anos pelo ano de nascimento."
            );
        }

        if (dataNascimento.isAfter(faixa.dataMaxima())) {

            throw new RegraNegocioException("O aluno deve ter pelo menos 6 meses completos.");
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

    private FaixaNascimento faixaNascimento() {

        var hoje = LocalDate.now(clock.withZone(FUSO_CRECHE));
        var dataMinima = LocalDate.of(hoje.getYear() - IDADE_MAXIMA_ANOS, 1, 1);
        var dataMaxima = hoje.minusMonths(IDADE_MINIMA_MESES);

        if (hoje.getDayOfMonth() == hoje.lengthOfMonth()) {
            dataMaxima = dataMaxima.with(TemporalAdjusters.lastDayOfMonth());
        }

        return new FaixaNascimento(dataMinima, dataMaxima);
    }

    private ConviteCadastro buscarDisponivel(String token) {

        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {

            throw conviteInvalido();
        }

        var convite = convites.buscarPorTokenHashComBloqueio(tokens.calcularHash(token))
                .orElseThrow(this::conviteInvalido);

        if (convite.getPerfil() != Perfil.RESPONSAVEL || !convite.estaDisponivel(clock.instant())) {

            throw conviteInvalido();
        }

        return convite;
    }

    private RegraNegocioException conviteInvalido() {

        return new RegraNegocioException("Convite inválido, expirado, cancelado ou já utilizado.");
    }

    public record DadosConviteResponsavel(String nome, String email, Instant expiraEm, TermoService.DocumentosCadastro documentos, FaixaNascimento faixaNascimento) {

    }

    public record FaixaNascimento(LocalDate dataMinima, LocalDate dataMaxima) {

    }

    public record CadastroResponsavelResultado(UUID usuarioId, UUID alunoId, String mensagem) {

    }
}