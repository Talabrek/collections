// Fetch server status on page load
document.addEventListener('DOMContentLoaded', function() {
    const statusBox = document.getElementById('status');

    fetch('/api/status')
        .then(response => {
            if (response.status === 401) {
                statusBox.innerHTML = '<h3>Authentication Required</h3><p>Please log in to view server status.</p>';
                statusBox.classList.add('error');
                return null;
            }
            return response.json();
        })
        .then(data => {
            if (data) {
                statusBox.innerHTML = `
                    <h3>Server Status</h3>
                    <p><strong>Status:</strong> ${data.status}</p>
                    <p><strong>Version:</strong> ${data.version}</p>
                    <p><strong>Collections:</strong> ${data.collections}</p>
                    <p><strong>Zones:</strong> ${data.zones}</p>
                `;
                statusBox.classList.add('success');
            }
        })
        .catch(error => {
            statusBox.innerHTML = '<h3>Connection Error</h3><p>Could not connect to server.</p>';
            statusBox.classList.add('error');
        });
});
