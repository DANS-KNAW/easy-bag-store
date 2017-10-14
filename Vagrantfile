system "ansible-galaxy install --force --role-file=#{File.dirname(File.expand_path(__FILE__))}/src/main/ansible/requirements.yml"
Vagrant.configure(2) do |config|
   config.vm.define "test" do |test|
      test.vm.box = "geerlingguy/centos6"
      test.vm.hostname = "test"
      test.vm.network :private_network, ip: "192.168.33.32"
      test.vm.network "forwarded_port", guest: 80, host: 8080
      test.vm.provision "ansible" do |ansible|
        ansible.playbook = "src/main/ansible/vagrant.yml"
        ansible.config_file = "src/main/ansible/ansible.cfg"
#        ansible.verbose = "vvvv"
      end
      test.vm.provider "virtualbox" do |vb|
        vb.gui = false
        vb.memory = 2072
        vb.cpus = 2
        vb.customize ["guestproperty", "set", :id, "--timesync-threshold", "1000"]
        vb.customize ["guestproperty", "set", :id, "--timesync-interval", "1000"]
      end
   end
end
