/*
 * L13 BalanceWebConfig: liga as propriedades da API, seguindo o padrão das demais configurações do contexto.
 *
 * Enunciado: O que será avaliado → Tratamento de cenários adversos
 */
package br.com.itau.challenge.balance.adapter.input.web

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(BalanceApiProperties::class)
class BalanceWebConfig
