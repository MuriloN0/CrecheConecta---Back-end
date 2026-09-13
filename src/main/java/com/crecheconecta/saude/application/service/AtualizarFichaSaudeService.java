package com.crecheconecta.saude.application.service;

import com.crecheconecta.saude.application.exception.FichaSaudeNaoEncontradaException;
import com.crecheconecta.saude.application.port.in.AtualizarFichaSaudeUseCase;
import com.crecheconecta.saude.application.port.in.CadastrarFichaSaudeUseCase.ArquivoUpload;
import com.crecheconecta.saude.application.port.out.ArmazenamentoArquivoPort;
import com.crecheconecta.saude.application.port.out.CriptografiaPort;
import com.crecheconecta.saude.application.port.out.FichaSaudeRepositoryPort;
import com.crecheconecta.saude.domain.model.Anexo;
import com.crecheconecta.saude.domain.model.FichaSaude;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AtualizarFichaSaudeService implements AtualizarFichaSaudeUseCase {

    private final FichaSaudeRepositoryPort repository;
    private final ArmazenamentoArquivoPort armazenamento;
    private final CriptografiaPort cripto;
    private final PoliticaAcessoDadosSaude politica;
    private final Clock clock;

    public AtualizarFichaSaudeService(
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
    public FichaSaude executar(Comando comando) {
        politica.exigirAcesso(comando.usuarioId(), comando.alunoId());

        FichaSaude atual = repository.buscarPorId(comando.fichaId())
                .orElseThrow(() -> new FichaSaudeNaoEncontradaException(comando.fichaId()));
        politica.exigirPropriedade(atual, comando.alunoId());

        List<UUID> mantidosIds =
                comando.anexosMantidos() == null ? List.of() : comando.anexosMantidos();
        List<ArquivoUpload> novos =
                comando.novosArquivos() == null ? List.of() : comando.novosArquivos();

        List<Anexo> mantidos = atual.anexos().stream()
                .filter(a -> mantidosIds.contains(a.id()))
                .toList();
        List<Anexo> descartados = atual.anexos().stream()
                .filter(a -> !mantidosIds.contains(a.id()))
                .toList();

        ValidadorAnexo.validarQuantidade(mantidos.size() + novos.size());
        novos.forEach(ValidadorAnexo::validarArquivo);

        List<Anexo> resultado = new ArrayList<>(mantidos);
        List<String> chavesNovas = new ArrayList<>();
        try {
            for (ArquivoUpload arquivo : novos) {
                String chave = armazenamento.armazenar(
                        arquivo.nomeArquivo(), arquivo.tipoConteudo(), arquivo.conteudo());
                chavesNovas.add(chave);
                resultado.add(new Anexo(
                        UUID.randomUUID(), arquivo.nomeArquivo(), chave,
                        arquivo.tipoConteudo(), arquivo.conteudo().length));
            }

            String obsCifrada = comando.observacoes() == null
                    ? null
                    : cripto.cifrar(comando.observacoes());

            FichaSaude atualizada = atual.comAlteracoes(
                    comando.nome(), obsCifrada, resultado, clock.instant());
            repository.salvar(atualizada);

            for (Anexo descartado : descartados) {
                try {
                    armazenamento.remover(descartado.chaveArmazenamento());
                } catch (RuntimeException ignorado) {

                }
            }

            String obsClara = obsCifrada == null ? null : cripto.decifrar(obsCifrada);
            return atualizada.comAlteracoes(
                    atualizada.nome(), obsClara, atualizada.anexos(),
                    atualizada.dataAtualizacao());
        } catch (RuntimeException erro) {
            for (String chave : chavesNovas) {
                try {
                    armazenamento.remover(chave);
                } catch (RuntimeException ignorado) {

                }
            }
            throw erro;
        }
    }
}
