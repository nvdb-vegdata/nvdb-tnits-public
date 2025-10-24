import { formatFileSize, formatTimestamp } from './utils.js'

let selectedType = null

document.addEventListener('DOMContentLoaded', () => {
  void loadFeatureTypes()

  document
    .getElementById('updates-from')
    ?.addEventListener('change', (event) => {
      if (selectedType) {
        void loadUpdates(selectedType, event.target.value)
      }
    })
})

async function loadFeatureTypes() {
  try {
    const response = await fetch('/api/v1/tnits/types')
    const types = await response.json()

    const container = document.getElementById('feature-types-list')
    container.innerHTML = types
      .map(
        (type) => `
      <button class="feature-type-btn" data-type="${type}" onclick="selectFeatureType('${type}')">
        <span class="type-code">${type}</span>
      </button>
    `,
      )
      .join('')
  } catch (error) {
    console.error('Failed to load feature types:', error)
    document.getElementById('feature-types-list').innerHTML =
      '<p class="error">Failed to load feature types</p>'
  }
}

window.selectFeatureType = function (type) {
  selectedType = type

  document.querySelectorAll('.feature-type-btn').forEach((btn) => {
    btn.classList.remove('active')
  })
  document.querySelector(`[data-type="${type}"]`)?.classList.add('active')

  document.getElementById('dataset-details').classList.remove('hidden')

  void loadSnapshots(type)
  void loadUpdates(type)
}

async function loadSnapshots(type) {
  const container = document.getElementById('snapshots-table')
  container.innerHTML =
    '<tr><td colspan="3" class="loading">Loading snapshots...</td></tr>'

  try {
    const response = await fetch(`/api/v1/tnits/${type}/snapshots`)
    if (!response.ok) {
      throw new Error('No snapshots found')
    }

    const data = await response.json()
    if (data.snapshots) {
      renderSnapshots(data.snapshots, container)
    }
  } catch (error) {
    console.error('Failed to load snapshots:', error)
    container.innerHTML =
      '<tr><td colspan="3" class="empty">No snapshots available</td></tr>'
  }
}

async function loadUpdates(type, from = null) {
  const container = document.getElementById('updates-table')
  container.innerHTML =
    '<tr><td colspan="3" class="loading">Loading updates...</td></tr>'

  const fromParam = from || document.getElementById('updates-from')?.value || ''
  const url = `/api/v1/tnits/${type}/updates${fromParam ? `?from=${fromParam}` : ''}`

  try {
    const response = await fetch(url)
    const data = await response.json()

    if (data.updates && data.updates.length > 0) {
      renderUpdates(data.updates, container)
    } else {
      container.innerHTML =
        '<tr><td colspan="3" class="empty">No updates available for this period</td></tr>'
    }
  } catch (error) {
    console.error('Failed to load updates:', error)
    container.innerHTML =
      '<tr><td colspan="3" class="error">Failed to load updates</td></tr>'
  }
}

function renderSnapshots(snapshots, container) {
  container.innerHTML = snapshots
    .map(
      (snapshot) => `
    <tr>
      <td data-timestamp="${snapshot.timestamp}">${snapshot.timestamp}</td>
      <td data-size="${snapshot.size}">${snapshot.size}</td>
      <td><a href="${snapshot.href}" class="download-btn" download>Download</a></td>
    </tr>
  `,
    )
    .join('')
  formatTableData(container)
}

function renderUpdates(updates, container) {
  container.innerHTML = updates
    .map(
      (update) => `
    <tr>
      <td data-timestamp="${update.timestamp}">${update.timestamp}</td>
      <td data-size="${update.size}">${update.size}</td>
      <td><a href="${update.href}" class="download-btn" download>Download</a></td>
    </tr>
  `,
    )
    .join('')
  formatTableData(container)
}

function formatTableData(table) {
  table.querySelectorAll('[data-timestamp]').forEach((cell) => {
    cell.textContent = formatTimestamp(cell.dataset.timestamp)
  })

  table.querySelectorAll('[data-size]').forEach((cell) => {
    cell.textContent = formatFileSize(parseInt(cell.dataset.size))
  })
}
