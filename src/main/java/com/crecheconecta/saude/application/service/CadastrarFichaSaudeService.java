package com.crecheconecta.saude.application.service;

import com.crecheconecta.saude.application.port.in.CadastrarFichaSaudeUseCase;
import com.crecheconecta.saude.application.port.out.ArmazenamentoArquivoPort;
import com.crecheconecta.saude.application.port.out.CriptografiaPort;
import com.crecheconecta.saude.application.port.out.FichaSaudeRepositoryPort;
import com.crecheconecta.saude.domain.model.Anexo;
import com.crecheconecta.saude.domain.model.FichaSaude;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CadastrarFichaSaudeService implements CadastrarFichaSaudeUseCase {

    private final FichaSaudeRepositoryPort repository;
    private final ArmazenamentoArquivoPort armazenamento;
    private final CriptografiaPort cripto;
    private final PoliticaAcessoDadosSaude politica;
    private final Clock clock;

    public CadastrarFichaSaudeService(
            FichaSaudeRepositoryPort repository,
            ArmazenamentoArquivoPort armazenamento,
            CriptografiaPort cripto,
            PoliticaAcessoDadosSaude politica,
            Clock clock
    ) {
        this.repository = repository;
        this.armazenamento = armazenamento;
        this.cripto = cripto;
        this.politica = politica;
        this.clock = clock;
    }

    @Override
    public UUID executar(Comando comando) {
        politica.exigirAcesso(comando.usuarioId(), comando.alunoId());

        List<ArquivoUpload> arquivos =
                comando.arquivos() == null ? List.of() : comando.arquivos();

        ValidadorAnexo.validarLote(arquivos);

        List<Anexo> anexos = new ArrayList<>();
        List<String> chavesArmazenadas = new ArrayList<>();
        try {
            for (ArquivoUpload arquivo : arquivos) {
                String chave = armazenamento.armazenar(
                        arquivo.nomeArquivo(), arquivo.tipoConteudo(), arquivo.conteudo());
                chavesArmazenadas.add(chave);
                anexos.add(new Anexo(
                        UUID.randomUUID(), arquivo.nomeArquivo(), chave,
                        arquivo.tipoConteudo(), arquivo.conteudo().length));
            }

            String obsCifrada = comando.observacoes() == null
                    ? null
                    : cripto.cifrar(comando.observacoes());

            FichaSaude ficha = FichaSaude.criar(
                    comando.alunoId(), comando.nome(), obsCifrada, anexos, clock.instant());
            repository.salvar(ficha);
            return ficha.id();
        } catch (RuntimeException erro) {
            for (String chave : chavesArmazenadas) {
                try {
                    armazenamento.remover(chave);
                } catch (RuntimeException ignorado) {
                    // best-effort de limpeza
                }
            }
            throw erro;
        }
    }
}
