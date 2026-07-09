foreach ($p in 19050, 19051, 9050, 9051) {
    netsh interface portproxy delete v4tov4 listenaddress=127.0.0.1 listenport=$p 2>$null | Out-Null
}
netsh interface portproxy show v4tov4
