.DEFAULT_GOAL := help
current_dir = $(shell pwd)

help: Makefile
	@echo Choose a command to run

license-check:
	trivy fs -q --scanners license --exit-code 0 --output license-scan.txt --debug .

vuln-check:
	trivy fs -q --scanners vuln --exit-code 0 --output vuln-scan.txt .

secret-check:
	trivy fs -q --scanners secret --exit-code 0 --output secret-scan.txt .

security-check:
	make secret-check && make vuln-check && make secret-check