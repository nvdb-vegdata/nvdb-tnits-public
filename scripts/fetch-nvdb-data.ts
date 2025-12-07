#!/usr/bin/env bun

import { mkdir, writeFile } from 'fs/promises'
import { join } from 'path'

const NVDB_API_BASE = 'https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1'
const TEST_RESOURCES_DIR = 'tnits-generator/src/test/resources'

interface StedfestingLinje {
  id: number
  startposisjon: number
  sluttposisjon: number
  retning: string
  kjorefelt?: string[]
}

interface Vegobjekt {
  id: number
  typeId: number
  stedfesting: {
    type: string
    linjer: StedfestingLinje[]
  }
}

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(
      `Failed to fetch ${url}: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

async function saveJson(filename: string, data: unknown): Promise<void> {
  await mkdir(TEST_RESOURCES_DIR, { recursive: true })
  const filepath = join(TEST_RESOURCES_DIR, filename)
  await writeFile(filepath, JSON.stringify(data, null, 2) + '\n')
  console.log(`✓ Saved ${filename}`)
}

async function fetchVegobjektData(
  typeId: number,
  vegobjektId: number,
): Promise<void> {
  console.log(`Fetching vegobjekt ${typeId}/${vegobjektId}...`)

  const vegobjektUrl = `${NVDB_API_BASE}/vegobjekter/${typeId}/${vegobjektId}?inkluder=alle`
  const vegobjekt = await fetchJson<Vegobjekt>(vegobjektUrl)

  const vegobjektFilename = `vegobjekt-${typeId}-${vegobjektId}.json`
  await saveJson(vegobjektFilename, vegobjekt)

  const veglenkesekvensIds = vegobjekt.stedfesting.linjer.map(
    (linje) => linje.id,
  )

  if (veglenkesekvensIds.length === 0) {
    console.log('⚠ No veglenkesekvenser found in stedfesting.linjer')
    return
  }

  if (veglenkesekvensIds.length === 1) {
    const id = veglenkesekvensIds[0]
    console.log(`Fetching veglenkesekvens: ${id}...`)

    const veglenkesekvensUrl = `${NVDB_API_BASE}/vegnett/veglenkesekvenser/${id}`
    const veglenkesekvens = await fetchJson<unknown>(veglenkesekvensUrl)

    const veglenkesekvensFilename = `veglenkesekvens-${id}.json`
    await saveJson(veglenkesekvensFilename, veglenkesekvens)
  } else {
    console.log(
      `Fetching ${veglenkesekvensIds.length} veglenkesekvenser: ${veglenkesekvensIds.join(', ')}...`,
    )

    const veglenkesekvensUrl = `${NVDB_API_BASE}/vegnett/veglenkesekvenser?ider=${veglenkesekvensIds.join(',')}`
    const veglenkesekvenser = await fetchJson<unknown>(veglenkesekvensUrl)

    const firstId = veglenkesekvensIds[0]
    const lastId = veglenkesekvensIds[veglenkesekvensIds.length - 1]
    const veglenkesekvensFilename = `veglenkesekvenser-${firstId}-${lastId}.json`
    await saveJson(veglenkesekvensFilename, veglenkesekvenser)
  }

  console.log('✓ Done!')
}

async function main() {
  const args = process.argv.slice(2)

  if (args.length !== 2) {
    console.error(
      'Usage: bun scripts/fetch-nvdb-data.ts <typeId> <vegobjektId>',
    )
    console.error('Example: bun scripts/fetch-nvdb-data.ts 591 85341377')
    process.exit(1)
  }

  const typeId = parseInt(args[0], 10)
  const vegobjektId = parseInt(args[1], 10)

  if (isNaN(typeId) || isNaN(vegobjektId)) {
    console.error('Error: typeId and vegobjektId must be valid numbers')
    process.exit(1)
  }

  try {
    await fetchVegobjektData(typeId, vegobjektId)
  } catch (error) {
    console.error('Error:', error instanceof Error ? error.message : error)
    process.exit(1)
  }
}

main()
